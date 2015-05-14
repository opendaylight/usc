/*
 *   stunnel       Universal SSL tunnel
 *   Copyright (C) 1998-2015 Michal Trojnara <Michal.Trojnara@mirt.net>
 *
 *   This program is free software; you can redistribute it and/or modify it
 *   under the terms of the GNU General Public License as published by the
 *   Free Software Foundation; either version 2 of the License, or (at your
 *   option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *   See the GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License along
 *   with this program; if not, see <http://www.gnu.org/licenses>.
 *
 *   Linking stunnel statically or dynamically with other modules is making
 *   a combined work based on stunnel. Thus, the terms and conditions of
 *   the GNU General Public License cover the whole combination.
 *
 *   In addition, as a special exception, the copyright holder of stunnel
 *   gives you permission to combine stunnel with free software programs or
 *   libraries that are released under the GNU LGPL and with code included
 *   in the standard release of OpenSSL under the OpenSSL License (or
 *   modified versions of such code, with unchanged license). You may copy
 *   and distribute such a system following the terms of the GNU GPL for
 *   stunnel and the licenses of the other code concerned.
 *
 *   Note that people who make modified versions of stunnel are not obligated
 *   to grant this special exception for their modified versions; it is their
 *   choice whether to do so. The GNU General Public License gives permission
 *   to release a modified version without this exception; this exception
 *   also makes it possible to release a modified version which carries
 *   forward this exception.
 */

#include "common.h"
#include "prototypes.h"



/* http://www.openssl.org/support/faq.html#PROG2 */
#ifdef USE_WIN32
#ifdef __GNUC__
#pragma GCC diagnostic push
#pragma GCC diagnostic ignored "-pedantic"
#endif /* __GNUC__ */
#ifdef __GNUC__
#include <../ms/applink.c>
#else /* __GNUC__ */
#include <openssl/applink.c>
#endif /* __GNUC__ */
#ifdef __GNUC__
#pragma GCC diagnostic pop
#endif /* __GNUC__ */
#endif /* USE_WIN32 */

/**************************************** prototypes */

#ifdef __INNOTEK_LIBC__
struct sockaddr_un
{
    u_char  sun_len;             /* sockaddr len including null */
    u_char  sun_family;          /* AF_OS2 or AF_UNIX */
    char    sun_path[108];       /* path name */
};
#endif

NOEXPORT int accept_connection ( SERVICE_OPTIONS * );
#ifdef HAVE_CHROOT
NOEXPORT int change_root ( void );
#endif
NOEXPORT int signal_pipe_init ( void );
NOEXPORT int signal_pipe_dispatch ( void );
#ifdef USE_FORK
NOEXPORT void client_status ( void ); /* dead children detected */
#endif

void hexDebugDump ( unsigned char *data, size_t size );

/**************************************** global variables */

static int signal_pipe[2] = { -1, -1};

#ifndef USE_FORK
long max_clients = 0;
/* -1 before a valid config is loaded, then the current number of clients */
volatile long num_clients = -1;
#endif
s_poll_set *fds; /* file descriptors of listening sockets */
int systemd_fds; /* number of file descriptors passed by systemd */
int listen_fds_start; /* base for systemd-provided file descriptors */

extern CLI *glogal_c;
/**************************************** startup */

void main_init()
{
    /* one-time initialization */
#ifdef USE_SYSTEMD
    int i;

    systemd_fds = sd_listen_fds ( 1 );

    if ( systemd_fds < 0 )
        fatal ( "systemd initialization failed" );

    listen_fds_start = SD_LISTEN_FDS_START;

    /* set non-blocking mode on systemd file descriptors */
    for ( i = 0; i < systemd_fds; ++i )
        set_nonblock ( listen_fds_start + i, 1 );

#else
    systemd_fds = 0; /* no descriptors received */
    listen_fds_start = 3; /* the value is not really important */
#endif

    /* basic initialization contains essential functions required for logging
     * subsystem to function properly, thus all errors here are fatal */
    if ( ssl_init() ) /* initialize SSL library */
        fatal ( "SSL initialization failed" );

    if ( sthreads_init() ) /* initialize critical sections & SSL callbacks */
        fatal ( "Threads initialization failed" );

    options_defaults();
    options_apply();

#ifndef USE_FORK
    get_limits(); /* required by setup_fd() */
#endif

    fds = s_poll_alloc();

    if ( signal_pipe_init() )
        fatal ( "Signal pipe initialization failed: "
                "check your personal firewall" );

    stunnel_info ( LOG_NOTICE );
}

/* configuration-dependent initialization */
int main_configure ( char *arg1, char *arg2 )
{
    if ( options_cmdline ( arg1, arg2 ) )
        return 1;

    options_apply();
    str_canary_init(); /* needs prng initialization from options_cmdline */

#if !defined(USE_WIN32) && !defined(__vms)
    /* syslog_open() must be called before change_root()
     * to be able to access /dev/log socket */
    syslog_open();
#endif /* !defined(USE_WIN32) && !defined(__vms) */

    if ( bind_ports() )
        return 1;

#ifdef HAVE_CHROOT

    /* change_root() must be called before drop_privileges()
     * since chroot() needs root privileges */
    if ( change_root() )
        return 1;

#endif /* HAVE_CHROOT */

    if ( drop_privileges ( 1 ) )
        return 1;

    /* log_open() must be be called after drop_privileges()
     * or logfile rotation won't be possible */
    /* log_open() must be be called before daemonize()
     * since daemonize() invalidates stderr */
    if ( log_open() )
        return 1;

#ifndef USE_FORK
    num_clients = 0; /* the first valid config */
#endif
    return 0;
}

int drop_privileges ( int critical )
{
#if defined(USE_WIN32) || defined(__vms) || defined(USE_OS2)
    ( void ) critical; /* skip warning about unused parameter */
#else
#ifdef HAVE_SETGROUPS
    gid_t gr_list[1];
#endif

    /* set uid and gid */
    if ( global_options.gid )
    {
        if ( setgid ( global_options.gid ) && critical )
        {
            sockerror ( "setgid" );
            return 1;
        }

#ifdef HAVE_SETGROUPS
        gr_list[0] = global_options.gid;

        if ( setgroups ( 1, gr_list ) && critical )
        {
            sockerror ( "setgroups" );
            return 1;
        }

#endif
    }

    if ( global_options.uid )
    {
        if ( setuid ( global_options.uid ) && critical )
        {
            sockerror ( "setuid" );
            return 1;
        }
    }

#endif /* standard Unix */
    return 0;
}

void main_cleanup()
{
    unbind_ports();
    s_poll_free ( fds );
    fds = NULL;
#if 0
    str_stats(); /* main thread allocation tracking */
#endif
    log_flush ( LOG_MODE_ERROR );
}

/**************************************** Unix-specific initialization */

#ifndef USE_WIN32

#ifdef USE_FORK
NOEXPORT void client_status ( void )
{
    /* dead children detected */
    int pid, status;

#ifdef HAVE_WAIT_FOR_PID

    while ( ( pid = wait_for_pid ( -1, &status, WNOHANG ) ) > 0 )
    {
#else

    if ( ( pid = wait ( &status ) ) > 0 )
    {
#endif
#ifdef WIFSIGNALED

        if ( WIFSIGNALED ( status ) )
        {
            s_log ( LOG_DEBUG, "%s: Process %d terminated on signal %d",  __func__,
                    pid, WTERMSIG ( status ) );
        }
        else
        {
            s_log ( LOG_DEBUG, "%s: Process %d finished with code %d",  __func__,
                    pid, WEXITSTATUS ( status ) );
        }
    }

#else
        s_log ( LOG_DEBUG, "%s: Process %d finished with code %d",  __func__,
                pid, status );
    }

#endif
}
#endif /* defined USE_FORK */

#ifndef USE_OS2

void child_status ( void ) /* dead libwrap or 'exec' process detected */
{
    int pid, status;

#ifdef HAVE_WAIT_FOR_PID

    while ( ( pid = wait_for_pid ( -1, &status, WNOHANG ) ) > 0 )
    {
#else

    if ( ( pid = wait ( &status ) ) > 0 )
    {
#endif
#ifdef WIFSIGNALED

        if ( WIFSIGNALED ( status ) )
        {
            s_log ( LOG_INFO, "%s: Child process %d terminated on signal %d",  __func__,
                    pid, WTERMSIG ( status ) );
        }
        else
        {
            s_log ( LOG_INFO, "%s: Child process %d finished with code %d",  __func__,
                    pid, WEXITSTATUS ( status ) );
        }

#else
        s_log ( LOG_INFO, "%s: Child process %d finished with status %d",  __func__,
                pid, status );
#endif
    }
}

#endif /* !defined(USE_OS2) */

#endif /* !defined(USE_WIN32) */

/**************************************** main loop accepting connections */

void daemon_loop ( void )
{
    SERVICE_OPTIONS *opt;
    int temporary_lack_of_resources;

    while ( 1 )
    {
        temporary_lack_of_resources = 0;

        if ( s_poll_wait ( fds, -1, -1 ) >= 0 )
        {
            if ( s_poll_canread ( fds, signal_pipe[0] ) )
                if ( signal_pipe_dispatch() ) /* received SIGNAL_TERMINATE */
                    break; /* terminate daemon_loop */

            for ( opt = service_options.next; opt; opt = opt->next )
                if ( opt->option.accept && s_poll_canread ( fds, opt->fd ) )
                    if ( accept_connection ( opt ) )
                        temporary_lack_of_resources = 1;
        }
        else
        {
            log_error ( LOG_NOTICE, get_last_socket_error(),
                        "daemon_loop: s_poll_wait" );
            temporary_lack_of_resources = 1;
        }

        if ( temporary_lack_of_resources )
        {
            s_log ( LOG_NOTICE,
                    "Accepting new connections suspended for 1 second",  __func__ );
            sleep ( 1 ); /* to avoid log trashing */
        }
    }
}

/* return 1 when a short delay is needed before another try */
NOEXPORT int accept_connection ( SERVICE_OPTIONS *opt )
{
    SOCKADDR_UNION addr;
    char *from_address;
    int s;
    socklen_t addrlen;

    addrlen = sizeof addr;

    for ( ;; )
    {
        s = s_accept ( opt->fd, &addr.sa, &addrlen, 1, "local socket" );

        if ( s >= 0 ) /* success! */
            break;

        switch ( get_last_socket_error() )
        {
        case S_EINTR: /* interrupted by a signal */
            break; /* retry now */
        case S_EMFILE:
#ifdef S_ENFILE
        case S_ENFILE:
#endif
#ifdef S_ENOBUFS
        case S_ENOBUFS:
#endif
#ifdef S_ENOMEM
        case S_ENOMEM:
#endif
            return 1; /* temporary lack of resources */
        default:
            return 0; /* any other error */
        }
    }

    from_address = s_ntop ( &addr, addrlen );
    s_log ( LOG_DEBUG, "%s: Service [%s] accepted (FD=%d) from %s", __func__,
            opt->servname, s, from_address );
    str_free ( from_address );
#ifdef USE_FORK
    RAND_add ( "", 1, 0.0 ); /* each child needs a unique entropy pool */
#else

    if ( max_clients && num_clients >= max_clients )
    {
        s_log ( LOG_WARNING, "%s: Connection rejected: too many clients (>=%ld)",  __func__,
                max_clients );
        closesocket ( s );
        return 0;
    }

#endif

    if ( create_client ( opt->fd, s, alloc_client_session ( opt, s, s ), client_thread ) )
    {
        s_log ( LOG_ERR, "%s: Connection rejected: create_client failed",  __func__ );
        closesocket ( s );
        return 0;
    }

    return 0;
}

/**************************************** initialization helpers */

/* clear fds, close old ports */
void unbind_ports ( void )
{
    SERVICE_OPTIONS *opt;
#ifdef HAVE_STRUCT_SOCKADDR_UN
    struct stat st; /* buffer for stat */
#endif

    s_poll_init ( fds );
    s_poll_add ( fds, signal_pipe[0], 1, 0 );

    for ( opt = service_options.next; opt; opt = opt->next )
    {
        s_log ( LOG_DEBUG, "%s: Closing service [%s]",  __func__, opt->servname );

        if ( opt->option.accept && opt->fd >= 0 )
        {
            if ( opt->fd < listen_fds_start ||  opt->fd >= listen_fds_start + systemd_fds )
                closesocket ( opt->fd );

            s_log ( LOG_DEBUG, "%s: Service [%s] closed (FD=%d)",  __func__,  opt->servname, opt->fd );
            opt->fd = -1;
#ifdef HAVE_STRUCT_SOCKADDR_UN

            if ( opt->local_addr.sa.sa_family == AF_UNIX )
            {
                if ( lstat ( opt->local_addr.un.sun_path, &st ) )
                    sockerror ( opt->local_addr.un.sun_path );
                else if ( !S_ISSOCK ( st.st_mode ) )
                    s_log ( LOG_ERR, "%s: Not a socket: %s",  __func__, opt->local_addr.un.sun_path );
                else if ( unlink ( opt->local_addr.un.sun_path ) )
                    sockerror ( opt->local_addr.un.sun_path );
                else
                    s_log ( LOG_DEBUG, "%s: Socket removed: %s",  __func__, opt->local_addr.un.sun_path );
            }

#endif
        }
        else if ( opt->option.program && opt->option.remote )
        {
            /* create exec+connect services             */
            /* FIXME: this is just a crude workaround   */
            /*        is it better to kill the service? */
            opt->option.retry = 0;
        }

        /* purge session cache of the old SSL_CTX object */
        /* this workaround won't be needed anymore after */
        /* delayed deallocation calls SSL_CTX_free()     */
        if ( opt->ctx )
            SSL_CTX_flush_sessions ( opt->ctx, ( long ) time ( NULL ) + opt->session_timeout + 1 );

        s_log ( LOG_DEBUG, "%s: Service [%s] closed", __func__, opt->servname );
    }
}

/* open new ports, update fds */
int bind_ports ( void )
{
    SERVICE_OPTIONS *opt;
    char *local_address;
    int listening_section;
	CLI *c;

#ifdef USE_LIBWRAP
    /* execute after options_cmdline() to know service_options.next,
     * but as early as possible to avoid leaking file descriptors */
    /* retry on each bind_ports() in case stunnel.conf was reloaded
       without "libwrap = no" */
    libwrap_init();
#endif /* USE_LIBWRAP */

    /* allow clean unbind_ports() even though
       bind_ports() was not fully performed */
    for ( opt = service_options.next; opt; opt = opt->next )
        if ( opt->option.accept )
            opt->fd = -1;

    listening_section = 0;

    for ( opt = service_options.next; opt; opt = opt->next )
    {
        if ( opt->option.accept )
        {

            glogal_c = alloc_client_session (opt, -1, -1 );
            glogal_c->fds = s_poll_alloc(); 


            if ( opt->option.client )
            {
                glogal_c->sock_rfd = & ( glogal_c->local_rfd );
                glogal_c->sock_wfd = & ( glogal_c->local_wfd );
                glogal_c->ssl_rfd = glogal_c->ssl_wfd = & ( glogal_c->remote_fd );
            }
            else
            {
                glogal_c->sock_rfd = glogal_c->sock_wfd = & ( glogal_c->remote_fd );
                glogal_c->ssl_rfd = & ( glogal_c->local_rfd );
                glogal_c->ssl_wfd = & ( glogal_c->local_wfd );
            }
             
            if ( glogal_c->opt->option.connect_before_ssl )
            {
                remote_start ( glogal_c );
                protocol ( glogal_c, glogal_c->opt, PROTOCOL_MIDDLE );
                ssl_start ( glogal_c );                
            }
              
            if ( listening_section < systemd_fds )
            {
                opt->fd = listen_fds_start + listening_section;
                s_log ( LOG_DEBUG,
                        "Listening file descriptor received from systemd (FD=%d)",  __func__,
                        opt->fd );
            }
            else
            {
                opt->fd = s_socket ( opt->local_addr.sa.sa_family, SOCK_STREAM, 0, 1, "accept socket" );

                if ( opt->fd < 0 )
                    return 1;

                s_log ( LOG_DEBUG, "%s: Listening file descriptor created (FD=%d)",  __func__,
                        opt->fd );
            }

            if ( set_socket_options ( opt->fd, 0 ) < 0 )
            {
                closesocket ( opt->fd );
                opt->fd = -1;
                return 1;
            }

            /* local socket can't be unnamed */
            local_address = s_ntop ( &opt->local_addr, addr_len ( &opt->local_addr ) );

            /* we don't bind or listen on a socket inherited from systemd */
            if ( listening_section >= systemd_fds )
            {
                if ( bind ( opt->fd, &opt->local_addr.sa, addr_len ( &opt->local_addr ) ) )
                {
                    s_log ( LOG_ERR, "%s: Error binding service [%s] to %s",  __func__,
                            opt->servname, local_address );

                    sockerror ( "bind" );
                    closesocket ( opt->fd );
                    opt->fd = -1;
                    str_free ( local_address );
                    return 1;
                }

                if ( listen ( opt->fd, SOMAXCONN ) )
                {
                    sockerror ( "listen" );
                    closesocket ( opt->fd );
                    opt->fd = -1;
                    str_free ( local_address );
                    return 1;
                }
            }

            s_poll_init ( fds );
           // s_poll_add ( fds, signal_pipe[0], 1, 0 );
			s_poll_add ( fds, opt->fd, 1, 0 );
			s_log ( LOG_DEBUG, "%s: Service [%s] (FD=%d) bound to %s",  __func__, opt->servname, opt->fd, local_address );
			str_free ( local_address );
			++listening_section;



     
           // c->fds = s_poll_alloc();
         //  glogal_c->fds = fds; 
            
 

		}
		else if ( opt->option.program && opt->option.remote )
		{
			/* create exec+connect services */
			/* FIXME: needs to be delayed on reload with opt->option.retry set */
            create_client(-1, -1,
                alloc_client_session(opt, -1, -1), client_thread);
		}
	}


    if ( listening_section < systemd_fds )
    {
        s_log ( LOG_ERR,
                "%s: Too many listening file descriptors received from systemd, got %d",  __func__,
                systemd_fds );
        return 1;
    }

    return 0; /* OK */
}

#ifdef HAVE_CHROOT
NOEXPORT int change_root ( void )
{
    if ( !global_options.chroot_dir )
        return 0;

    if ( chroot ( global_options.chroot_dir ) )
    {
        sockerror ( "chroot" );
        return 1;
    }

    if ( chdir ( "/" ) )
    {
        sockerror ( "chdir" );
        return 1;
    }

    return 0;
}
#endif /* HAVE_CHROOT */

/**************************************** signal pipe handling */

NOEXPORT int signal_pipe_init ( void )
{
#ifdef USE_WIN32

    if ( make_sockets ( signal_pipe ) )
        return 1;

#elif defined(__INNOTEK_LIBC__)
    /* Innotek port of GCC can not use select on a pipe:
     * use local socket instead */
    struct sockaddr_un un;
    fd_set set_pipe;
    int pipe_in;

    FD_ZERO ( &set_pipe );

    signal_pipe[0] = s_socket ( PF_OS2, SOCK_STREAM, 0, 0, "socket#1" );
    signal_pipe[1] = s_socket ( PF_OS2, SOCK_STREAM, 0, 0, "socket#2" );

    /* connect the two endpoints */
    memset ( &un, 0, sizeof un );
    un.sun_len = sizeof un;
    un.sun_family = AF_OS2;

    sprintf ( un.sun_path, "\\socket\\stunnel-%u", getpid() );

    /* make the first endpoint listen */
    bind ( signal_pipe[0], ( struct sockaddr * ) &un, sizeof un );

    listen ( signal_pipe[0], 1 );

    connect ( signal_pipe[1], ( struct sockaddr * ) &un, sizeof un );

    FD_SET ( signal_pipe[0], &set_pipe );

    if ( select ( signal_pipe[0] + 1, &set_pipe, NULL, NULL, NULL ) > 0 )
    {
        pipe_in = signal_pipe[0];
        signal_pipe[0] = s_accept ( signal_pipe[0], NULL, 0, 0, "accept" );
        closesocket ( pipe_in );
    }
    else
    {
        sockerror ( "select" );
        return 1;
    }

#else /* Unix */

    if ( s_pipe ( signal_pipe, 1, "signal_pipe" ) )
        return 1;

#endif /* USE_WIN32 */
    return 0;
}

#ifdef __GNUC__
#pragma GCC diagnostic push
#pragma GCC diagnostic ignored "-Wunused-result"
#endif /* __GNUC__ */
void signal_post ( int sig )
{
    /* no meaningful way here to handle the result */
    writesocket ( signal_pipe[1], ( char * ) &sig, sizeof sig );
}
#ifdef __GNUC__
#pragma GCC diagnostic pop
#endif /* __GNUC__ */

NOEXPORT int signal_pipe_dispatch ( void )
{
    int sig;

    s_log ( LOG_DEBUG, "%s: Dispatching signals from the signal pipe",  __func__ );

    while ( readsocket ( signal_pipe[0], ( char * ) &sig, sizeof sig ) == sizeof sig )
    {
        switch ( sig )
        {
#ifndef USE_WIN32
        case SIGCHLD:
            s_log ( LOG_DEBUG, "%s: Processing SIGCHLD",  __func__ );
#ifdef USE_FORK
            client_status(); /* report status of client process */
#else /* USE_UCONTEXT || USE_PTHREAD */
            child_status();  /* report status of libwrap or 'exec' process */
#endif /* defined USE_FORK */
            break;
#endif /* !defind USE_WIN32 */
        case SIGNAL_RELOAD_CONFIG:
            s_log ( LOG_DEBUG, "%s: Processing SIGNAL_RELOAD_CONFIG",  __func__ );

            if ( options_parse ( CONF_RELOAD ) )
            {
                s_log ( LOG_ERR, "%s: Failed to reload the configuration file",  __func__ );
            }
            else
            {
                unbind_ports();
                log_close();
                options_apply();
                log_open();
                ui_config_reloaded();

                if ( bind_ports() )
                {
                    /* FIXME: handle the error */
                }
            }

            break;
        case SIGNAL_REOPEN_LOG:
            s_log ( LOG_DEBUG, "%s: Processing SIGNAL_REOPEN_LOG",  __func__ );
            log_close();
            log_open();
            s_log ( LOG_NOTICE, "%s: Log file reopened",  __func__ );
            break;
        case SIGNAL_TERMINATE:
            s_log ( LOG_DEBUG, "%s: Processing SIGNAL_TERMINATE",  __func__ );
            s_log ( LOG_NOTICE, "%s: Terminated",  __func__ );
            return 2;
        default:
            s_log ( LOG_ERR, "%s: Received signal %d; terminating",  __func__, sig );
            return 1;
        }
    }

    s_log ( LOG_DEBUG, "%s: Signal pipe is empty",  __func__ );
    return 0;
}

/**************************************** log build details */

void stunnel_info ( int level )
{
    s_log ( level, "%s: stunnel " STUNNEL_VERSION " netopeer on " HOST " platform",   __func__ );

    if ( SSLeay() == SSLEAY_VERSION_NUMBER )
    {
        s_log ( level, "Compiled/running with "  OPENSSL_VERSION_TEXT );
    }
    else
    {
        s_log ( level, "Compiled with " OPENSSL_VERSION_TEXT );
        s_log ( level, "Running  with %s", SSLeay_version ( SSLEAY_VERSION ) );
        s_log ( level, "Update OpenSSL shared libraries or rebuild stunnel" );
    }

    s_log ( level,

            "Threading:"
#ifdef USE_UCONTEXT
            "UCONTEXT"
#endif
#ifdef USE_PTHREAD
            "PTHREAD"
#endif
#ifdef USE_WIN32
            "WIN32"
#endif
#ifdef USE_FORK
            "FORK"
#endif

            " Sockets:"
#ifdef USE_POLL
            "POLL"
#else /* defined(USE_POLL) */
            "SELECT"
#endif /* defined(USE_POLL) */
            ",IPv%c"
#ifdef USE_SYSTEMD
            ",SYSTEMD"
#endif /* defined(USE_SYSTEMD) */

            " TLS:"
#ifndef OPENSSL_NO_ENGINE
#define TLS_FEATURE_FOUND
            "ENGINE"
#endif /* !defined(OPENSSL_NO_ENGINE) */
#ifdef USE_FIPS
#ifdef TLS_FEATURE_FOUND
            ","
#else
#define TLS_FEATURE_FOUND
#endif
            "FIPS"
#endif /* defined(USE_FIPS) */
#ifndef OPENSSL_NO_OCSP
#ifdef TLS_FEATURE_FOUND
            ","
#else
#define TLS_FEATURE_FOUND
#endif
            "OCSP"
#endif /* !defined(OPENSSL_NO_OCSP) */
#ifndef OPENSSL_NO_PSK
#ifdef TLS_FEATURE_FOUND
            ","
#else
#define TLS_FEATURE_FOUND
#endif
            "PSK"
#endif /* !defined(OPENSSL_NO_PSK) */
#ifndef OPENSSL_NO_TLSEXT
#ifdef TLS_FEATURE_FOUND
            ","
#else
#define TLS_FEATURE_FOUND
#endif
            "SNI"
#endif /* !defined(OPENSSL_NO_TLSEXT) */
#ifndef TLS_FEATURE_FOUND
            "NONE"
#endif /* !defined(TLS_FEATURE_FOUND) */

#ifdef USE_LIBWRAP
            " Auth:LIBWRAP"
#endif

            , /* supported IP version parameter */
#if defined(USE_WIN32) && !defined(_WIN32_WCE)
            s_getaddrinfo ? '6' : '4'
#else /* defined(USE_WIN32) */
#if defined(USE_IPv6)
            '6'
#else /* defined(USE_IPv6) */
            '4'
#endif /* defined(USE_IPv6) */
#endif /* defined(USE_WIN32) */
          );
#ifdef errno
#define xstr(a) str(a)
#define str(a) #a
    s_log ( LOG_DEBUG, "errno: " xstr ( errno ) );
#endif /* errno */
}


void hexDebugDump ( uint8_t *data, size_t size )
{
    unsigned char *p = data;
    unsigned char c;
    int n;
    char bytestr[4] = {0};
    char addrstr[10] = {0};
    char hexstr[ 16 * 3 + 5] = {0};
    char charstr[16 * 1 + 5] = {0};

    s_log ( LOG_DEBUG, " START data:%p size:%d\n", data, size );

    for ( n = 1; n <= size; n++ )
    {
        if ( n % 16 == 1 )
        {
            /* store address for this line */
            snprintf ( addrstr, sizeof ( addrstr ), "%.4x", ( ( unsigned int ) p - ( unsigned int ) data ) );
        }

        c = *p;

        if ( isalnum ( c ) == 0 )
        {
            c = '.';
        }

        /* store hex str (for left side) */
        snprintf ( bytestr, sizeof ( bytestr ), "%02X ", *p );
        strncat ( ( char * ) hexstr, ( char * ) bytestr, sizeof ( hexstr ) - strlen ( hexstr ) - 1 );
        /* store char str (for right side) */
        snprintf ( bytestr, sizeof ( bytestr ), "%c", c );
        strncat ( charstr, bytestr, sizeof ( charstr ) - strlen ( charstr ) - 1 );

        if ( n % 16 == 0 )
        {
            /* line completed */
            s_log ( LOG_DEBUG, "[%4.4s]   %-50.50s  %s\n",  addrstr,  hexstr,  charstr );
            hexstr[0] = 0;
            charstr[0] = 0;
        }
        else if ( n % 8 == 0 )
        {
            /* half line: add whitespaces */
            strncat ( hexstr,  "  ",  sizeof ( hexstr ) - strlen ( hexstr ) - 1 );
            strncat ( charstr, " ",  sizeof ( charstr ) - strlen ( charstr ) - 1 );
        }

        p++; /* next byte */
    }

    if ( strlen ( ( char * ) hexstr ) > 0 )
    {
        /* print rest of buffer if not empty */
        s_log ( LOG_DEBUG, "[%4.4s]   %-50.50s  %s\n",  addrstr,  hexstr,  charstr );
    }

    s_log ( LOG_DEBUG, " END data:%p size:%d\n", data, size );

}


/* end of stunnel.c */
