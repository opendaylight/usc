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


#ifndef SHUT_RD
#define SHUT_RD 0
#endif
#ifndef SHUT_WR
#define SHUT_WR 1
#endif
#ifndef SHUT_RDWR
#define SHUT_RDWR 2
#endif

char NETOPEER_AGENT[]="/usr/local/bin/netopeer-agent";
char NETOPEER_ARGS[]="netopeer-agent";

void client_try ( CLI * );
void client_run ( CLI * );
void local_start ( CLI * );
void remote_start ( CLI * );
void ssl_start ( CLI * );
void new_chain ( CLI * );
void transfer ( CLI * );
int parse_socket_error ( CLI *, const char * );

void print_cipher ( CLI * );
void auth_user ( CLI *, char * );
int connect_local ( CLI * );
int connect_remote ( CLI * );
void setup_connect_addr ( CLI * );
void local_bind ( CLI *c );
void print_bound_address ( CLI * );
void reset ( int, char * );

CLI *glogal_c;
int call_home_mode;

CLI *alloc_client_session ( SERVICE_OPTIONS *opt, int rfd, int wfd )
{
    CLI *c = str_alloc_detached ( sizeof ( CLI ) );
	c->opt = opt;
    c->local_rfd.fd = rfd;
    c->local_wfd.fd = wfd;
    return c;
}

void *client_thread ( void *arg )
{
    CLI *c = arg;

#ifdef DEBUG_STACK_SIZE
    stack_info ( 1 ); /* initialize */
#endif

    client_main ( c );

#ifdef DEBUG_STACK_SIZE
    stack_info ( 0 ); /* display computed value */
#endif

    str_stats(); /* client thread allocation tracking */
    str_cleanup();
    /* s_log() is not allowed after str_cleanup() */

#if defined(USE_WIN32) && !defined(_WIN32_WCE)
    _endthread();
#endif

#ifdef USE_UCONTEXT
    s_poll_wait ( NULL, 0, 0 ); /* wait on poll() */
#endif

    return NULL;
}


void client_main ( CLI *c )
{
    s_log ( LOG_DEBUG, "%s: Service [%s] started", __func__,  c->opt->servname );

    if ( c->opt->option.program && c->opt->option.remote )
    {
        /* exec and connect options specified together
         * -> spawn a local program instead of stdio */
        for ( ;; )
        {

            SERVICE_OPTIONS *opt = c->opt;
            memset ( c, 0, sizeof ( CLI ) ); /* connect_local needs clean c */
            c->opt = opt;

            if ( !setjmp ( c->err ) )
                c->local_rfd.fd = c->local_wfd.fd = connect_local ( c );
            else
                break;

            client_run ( c );

            if ( !c->opt->option.retry )
                break;

            sleep ( 1 ); /* FIXME: not a good idea in ucontext threading */
            s_poll_free ( c->fds );
            c->fds = NULL;
            str_stats(); /* client thread allocation tracking */

            /* c allocation is detached, so it is safe to call str_stats() */
            if ( service_options.next ) /* don't str_cleanup in inetd mode */
                str_cleanup();

        }

    }
    else
        client_run ( c );

    str_free ( c );
}


void client_run ( CLI *c )
{
    int err, rst;
#ifndef USE_FORK
    long num_clients_copy;
#endif

#ifndef USE_FORK
    enter_critical_section ( CRIT_CLIENTS );
    ui_clients ( ++num_clients );
    leave_critical_section ( CRIT_CLIENTS );
#endif

    s_log ( LOG_NOTICE, "%s: CLI: %p", __func__, c );

    /* initialize the client context */
    c->remote_fd.fd = -1;
    c->fd = -1;
    c->ssl = NULL;
    c->sock_bytes = c->ssl_bytes = 0;

    if ( c->opt->option.client )
    {
        c->sock_rfd = & ( c->local_rfd );
        c->sock_wfd = & ( c->local_wfd );
	//	c->ssl_rfd = c->ssl_wfd = & ( c->remote_fd );
        c->ssl_rfd = c->ssl_wfd = & ( glogal_c->remote_fd );
	    c->ssl = glogal_c->ssl;
	}
	else
	{
		c->sock_rfd = c->sock_wfd = & ( c->remote_fd );
		c->ssl_rfd = & ( c->local_rfd );
		c->ssl_wfd = & ( c->local_wfd );
	}

    c->fds = s_poll_alloc();
    addrlist_init ( &c->connect_addr );

    /* try to process the request */
    err = setjmp ( c->err );

    if ( !err )
        client_try ( c );

    rst = err == 1 && c->opt->option.reset;

    s_log ( LOG_NOTICE,
            "%s: Connection %s: %llu byte(s) sent to SSL, %llu byte(s) sent to socket", __func__,
            rst ? "reset" : "closed",
            ( unsigned long long ) c->ssl_bytes, ( unsigned long long ) c->sock_bytes );

    /* cleanup temporary (e.g. IDENT) socket */
    if ( c->fd >= 0 )
        closesocket ( c->fd );

    c->fd = -1;

    /* cleanup the SSL context */
    if ( c->ssl )
    {
        /* SSL initialized */
        SSL_set_shutdown ( c->ssl, SSL_SENT_SHUTDOWN | SSL_RECEIVED_SHUTDOWN );
        SSL_free ( c->ssl );
        c->ssl = NULL;

#if OPENSSL_VERSION_NUMBER>=0x10000000L
        ERR_remove_thread_state ( NULL );
#else /* OpenSSL version < 1.0.0 */
        ERR_remove_state ( 0 );
#endif /* OpenSSL version >= 1.0.0 */
    }

    /* cleanup the remote socket */
    if ( c->remote_fd.fd >= 0 )
    {
        /* remote socket initialized */
        if ( rst && c->remote_fd.is_socket ) /* reset */
            reset ( c->remote_fd.fd, "linger (remote)" );

        closesocket ( c->remote_fd.fd );
        s_log ( LOG_DEBUG, "%s: Remote socket (FD=%d) closed", __func__,  c->remote_fd.fd );
        c->remote_fd.fd = -1;
    }

    /* cleanup the local socket */
    if ( c->local_rfd.fd >= 0 )
    {
        /* local socket initialized */
        if ( c->local_rfd.fd == c->local_wfd.fd )
        {
            if ( rst && c->local_rfd.is_socket )
                reset ( c->local_rfd.fd, "linger (local)" );

            closesocket ( c->local_rfd.fd );
            s_log ( LOG_DEBUG, "%s: Local socket (FD=%d) closed", __func__,  c->local_rfd.fd );
        }
        else
        {
            /* stdin/stdout */
            if ( rst && c->local_rfd.is_socket )
                reset ( c->local_rfd.fd, "linger (local_rfd)" );

            if ( rst && c->local_wfd.is_socket )
                reset ( c->local_wfd.fd, "linger (local_wfd)" );
        }

        c->local_rfd.fd = c->local_wfd.fd = -1;
    }

#ifdef USE_FORK

    /* display child return code if it managed to arrive on time */
    /* otherwise it will be retrieved by the init process and ignored */
    if ( c->opt->option.program ) /* 'exec' specified */
        child_status(); /* null SIGCHLD handler was used */

    s_log ( LOG_DEBUG, "%s: Service [%s] finished", __func__,  c->opt->servname );

#else

    enter_critical_section ( CRIT_CLIENTS );
    ui_clients ( --num_clients );
    num_clients_copy = num_clients; /* to move s_log() away from CRIT_CLIENTS */
    leave_critical_section ( CRIT_CLIENTS );
    s_log ( LOG_DEBUG, "%s: Service [%s] finished (%ld left)", __func__,
            c->opt->servname, num_clients_copy );

#endif

    /* free the client context */
    if ( c->connect_addr.addr )
    {
        str_free ( c->connect_addr.addr );
        c->connect_addr.addr = NULL;
    }

    s_poll_free ( c->fds );
    c->fds = NULL;
}

void client_try ( CLI *c )
{
    s_log ( LOG_NOTICE, "%s: CLI: %p", __func__, c );


    local_start ( c );
    protocol ( c, c->opt, PROTOCOL_EARLY );

    if( !c->opt->option.client )
    {
        if ( c->opt->option.connect_before_ssl )
        {
            remote_start ( c );
            protocol ( c, c->opt, PROTOCOL_MIDDLE );
            ssl_start ( c );
        }
        else
        {
            ssl_start ( c );
            protocol ( c, c->opt, PROTOCOL_MIDDLE );
            remote_start ( c );
        }
    }

    protocol ( c, c->opt, PROTOCOL_LATE );
    transfer ( c );
}

void local_start ( CLI *c )
{

    SOCKADDR_UNION addr;
    socklen_t addr_len;
    char *accepted_address;
    
    s_log ( LOG_NOTICE, "%s: CLI: %p", __func__, c );


    /* check if local_rfd is a socket and get peer address */
    addr_len = sizeof ( SOCKADDR_UNION );
    c->local_rfd.is_socket = !getpeername ( c->local_rfd.fd, &addr.sa, &addr_len );

    if ( c->local_rfd.is_socket )
    {
        memcpy ( &c->peer_addr.sa, &addr.sa, addr_len );
        c->peer_addr_len = addr_len;

        if ( set_socket_options ( c->local_rfd.fd, 1 ) )
            s_log ( LOG_WARNING, "%s: Failed to set local socket options", __func__ );
    }
    else
    {

        if ( get_last_socket_error() != S_ENOTSOCK )
        {
            sockerror ( "getpeerbyname (local_rfd)" );
            longjmp ( c->err, 1 );
        }
    }

    /* check if local_wfd is a socket and get peer address */
    if ( c->local_rfd.fd == c->local_wfd.fd )
    {
        c->local_wfd.is_socket = c->local_rfd.is_socket;
    }
    else
    {
        addr_len = sizeof ( SOCKADDR_UNION );
        c->local_wfd.is_socket = !getpeername ( c->local_wfd.fd, &addr.sa, &addr_len );

        if ( c->local_wfd.is_socket )
        {
            if ( !c->local_rfd.is_socket )
            {
                /* already retrieved */
                memcpy ( &c->peer_addr.sa, &addr.sa, addr_len );
                c->peer_addr_len = addr_len;
            }

            if ( set_socket_options ( c->local_wfd.fd, 1 ) )
                s_log ( LOG_WARNING, "%s: Failed to set local socket options", __func__ );
        }
        else
        {
            if ( get_last_socket_error() != S_ENOTSOCK )
            {
                sockerror ( "getpeerbyname (local_wfd)" );
                longjmp ( c->err, 1 );
            }
        }
    }

    /* neither of local descriptors is a socket */
    if ( !c->local_rfd.is_socket && !c->local_wfd.is_socket )
    {

#ifndef USE_WIN32

        if ( c->opt->option.transparent_src )
        {
            s_log ( LOG_ERR, "%s: Transparent source needs a socket", __func__ );
            longjmp ( c->err, 1 );
        }

#endif
        s_log ( LOG_NOTICE, "%s: Service [%s] accepted connection", __func__,  c->opt->servname );
        return;
    }

    /* authenticate based on retrieved IP address of the client */
    accepted_address = s_ntop ( &c->peer_addr, c->peer_addr_len );

#ifdef USE_LIBWRAP
    libwrap_auth ( c, accepted_address );
#endif /* USE_LIBWRAP */

    auth_user ( c, accepted_address );

    s_log ( LOG_NOTICE, "%s: Service [%s] accepted connection from %s", __func__, c->opt->servname, accepted_address );

    str_free ( accepted_address );
}

void remote_start ( CLI *c )
{
    s_log ( LOG_NOTICE, "%s: CLI: %p", __func__, c );

    /* where to bind connecting socket */
    if ( c->opt->option.local ) /* outgoing interface */
        c->bind_addr = &c->opt->source_addr;

#ifndef USE_WIN32
    else if ( c->opt->option.transparent_src )
        c->bind_addr = &c->peer_addr;

#endif
    else
        c->bind_addr = NULL; /* don't bind */

    /* setup c->remote_fd, now */
    if ( c->opt->option.remote
#ifndef USE_WIN32
            || c->opt->option.transparent_dst
#endif
       )
    {
        /* try remote first for exec+connect targets */
        c->remote_fd.fd = connect_remote ( c );
    }
    else if ( c->opt->option.program )
    {
        /* exec+connect uses local fd */
		if(c->opt->option.client)
			c->remote_fd.fd = connect_local ( c );
    }
    else
    {
        s_log ( LOG_ERR, "%s: INTERNAL ERROR: No target for remote socket", __func__ );
        longjmp ( c->err, 1 );
    }

	if(c->opt->option.client)
	{
		c->remote_fd.is_socket = 1; /* always! */
		s_log ( LOG_DEBUG, "%s: Remote socket (FD=%d) initialized", __func__,  c->remote_fd.fd );

		if ( set_socket_options ( c->remote_fd.fd, 2 ) )
			s_log ( LOG_WARNING, "%s: Failed to set remote socket options", __func__ );
	}	
	
	s_log ( LOG_DEBUG, "%s: Remote socket (FD=%d) initialized", __func__,  c->remote_fd.fd );
}

void ssl_start ( CLI *c )
{
    int i, err;
    SSL_SESSION *old_session;
    int unsafe_openssl;

    c->ssl = SSL_new ( c->opt->ctx );

    if ( !c->ssl )
    {
        sslerror ( "SSL_new" );
        longjmp ( c->err, 1 );
    }

    SSL_set_ex_data ( c->ssl, cli_index, c ); /* for callbacks */

    if ( c->opt->option.client )
    {
#ifndef OPENSSL_NO_TLSEXT

        if ( c->opt->sni )
        {
            s_log ( LOG_INFO, "%s: SNI: sending servername: %s", __func__, c->opt->sni );

            if ( !SSL_set_tlsext_host_name ( c->ssl, c->opt->sni ) )
            {
                sslerror ( "SSL_set_tlsext_host_name" );
                longjmp ( c->err, 1 );
            }
        }

#endif

        if ( c->opt->session )
        {
            enter_critical_section ( CRIT_SESSION );
            SSL_set_session ( c->ssl, c->opt->session );
            leave_critical_section ( CRIT_SESSION );
        }

        SSL_set_fd ( c->ssl, c->remote_fd.fd );
        SSL_set_connect_state ( c->ssl );
    }
    else
    {
        if ( c->local_rfd.fd == c->local_wfd.fd )
            SSL_set_fd ( c->ssl, c->local_rfd.fd );
        else
        {
            /* does it make sense to have SSL on STDIN/STDOUT? */
            SSL_set_rfd ( c->ssl, c->local_rfd.fd );
            SSL_set_wfd ( c->ssl, c->local_wfd.fd );
        }

        SSL_set_accept_state ( c->ssl );
    }

    unsafe_openssl = SSLeay() < 0x0090810fL || ( SSLeay() >= 0x10000000L && SSLeay() < 0x1000002fL );

    while ( 1 )
    {
        /* critical section for OpenSSL version < 0.9.8p or 1.x.x < 1.0.0b *
         * this critical section is a crude workaround for CVE-2010-3864   *
         * see http://www.securityfocus.com/bid/44884 for details          *
         * alternative solution is to disable internal session caching     *
         * NOTE: this critical section also covers callbacks (e.g. OCSP)   */
        if ( unsafe_openssl )
            enter_critical_section ( CRIT_SSL );

        if ( c->opt->option.client )
            i = SSL_connect ( c->ssl );
        else
            i = SSL_accept ( c->ssl );

        if ( unsafe_openssl )
            leave_critical_section ( CRIT_SSL );

        err = SSL_get_error ( c->ssl, i );

        if ( err == SSL_ERROR_NONE )
            break; /* ok -> done */

        if ( err == SSL_ERROR_WANT_READ || err == SSL_ERROR_WANT_WRITE )
        {

            s_poll_init ( c->fds );
            s_poll_add ( c->fds, c->ssl_rfd->fd,
                         err == SSL_ERROR_WANT_READ,
                         err == SSL_ERROR_WANT_WRITE );

            switch ( s_poll_wait ( c->fds, c->opt->timeout_busy, 0 ) )
            {
            case -1:
                sockerror ( "ssl_start: s_poll_wait" );
                longjmp ( c->err, 1 );

            case 0:
                s_log ( LOG_INFO, "%s: ssl_start: s_poll_wait:"
                        " TIMEOUTbusy exceeded: sending reset", __func__ );
                longjmp ( c->err, 1 );

            case 1:
                break; /* OK */

            default:
                s_log ( LOG_ERR, "%s: ssl_start: s_poll_wait: unknown result", __func__ );
                longjmp ( c->err, 1 );
            }

            continue; /* ok -> retry */
        }

        if ( err == SSL_ERROR_SYSCALL )
        {
            switch ( get_last_socket_error() )
            {
            case S_EINTR:
            case S_EWOULDBLOCK:
#if S_EAGAIN!=S_EWOULDBLOCK
            case S_EAGAIN:
#endif
                continue;
            }
        }

        if ( c->opt->option.client )
            sslerror ( "SSL_connect" );
        else
            sslerror ( "SSL_accept" );

        longjmp ( c->err, 1 );

    }

    if ( SSL_session_reused ( c->ssl ) )
    {
        s_log ( LOG_INFO, "%s: SSL %s: previous session reused", __func__,
                c->opt->option.client ? "connected" : "accepted" );
    }
    else
    {
        /* a new session was negotiated */
        new_chain ( c );

        if ( c->opt->option.client )
        {
            s_log ( LOG_INFO, "%s: SSL connected: new session negotiated", __func__ );
            enter_critical_section ( CRIT_SESSION );
            old_session = c->opt->session;
            c->opt->session = SSL_get1_session ( c->ssl ); /* store it */

            if ( old_session )
                SSL_SESSION_free ( old_session ); /* release the old one */

            leave_critical_section ( CRIT_SESSION );
        }
        else
            s_log ( LOG_INFO, "%s: SSL accepted: new session negotiated", __func__ );

        print_cipher ( c );
    }
}

void new_chain ( CLI *c )
{
    BIO *bio;
    int i, len;
    X509 *peer = NULL;
    STACK_OF ( X509 ) *sk;
    char *chain;

    if ( c->opt->chain ) /* already cached */
        return; /* this race condition is safe to ignore */

    bio = BIO_new ( BIO_s_mem() );

    if ( !bio )
        return;

    sk = SSL_get_peer_cert_chain ( c->ssl );

    for ( i = 0; sk && i < sk_X509_num ( sk ); i++ )
    {
        peer = sk_X509_value ( sk, i );
        PEM_write_bio_X509 ( bio, peer );
    }

    if ( !sk || !c->opt->option.client )
    {
        peer = SSL_get_peer_certificate ( c->ssl );

        if ( peer )
        {
            PEM_write_bio_X509 ( bio, peer );
            X509_free ( peer );
        }
    }

    len = BIO_pending ( bio );

    if ( len <= 0 )
    {
        s_log ( LOG_INFO, "%s: No peer certificate received", __func__ );
        BIO_free ( bio );
        return;
    }

    /* prevent automatic deallocation of the cached value */
    chain = str_alloc_detached ( ( size_t ) len + 1 );
    len = BIO_read ( bio, chain, len );

    if ( len < 0 )
    {
        s_log ( LOG_ERR, "%s: BIO_read failed", __func__ );
        BIO_free ( bio );
        str_free ( chain );
        return;
    }

    chain[len] = '\0';
    BIO_free ( bio );
    c->opt->chain = chain; /* this race condition is safe to ignore */
    ui_new_chain ( c->opt->section_number );
    s_log ( LOG_DEBUG, "%s: Peer certificate was cached (%d bytes)", __func__, len );
}

/****************************** transfer data */
void transfer ( CLI *c )
{
    int watchdog = 0; /* a counter to detect an infinite loop */
    ssize_t num;

    int err;
    int usc_size;
    char *tmp_ptr;
    int sock_open_rd = 0, sock_open_wr = 0;
	
    /* awaited conditions on SSL file descriptors */
    int shutdown_wants_read = 0, shutdown_wants_write = 0;
    int read_wants_read = 0, read_wants_write = 0;
    int write_wants_read = 0, write_wants_write = 0;
    /* actual conditions on file descriptors */
    int sock_can_rd, sock_can_wr, ssl_can_rd, ssl_can_wr;
    usc_header *read_usc;
    usc_header write_usc;
    int rd_sock_closed = 0;
#ifdef USE_WIN32
    unsigned long bytes;
#else
    int bytes;
#endif

    /* logical channels (not file descriptors!) open for read or write */
    if( c->opt->option.client )
        sock_open_rd = 1, sock_open_wr = 1;


    if ( c->remote_fd.fd && !c->opt->option.client )
    {
        c->remote_fd.is_socket = 1; /* always! */;
        c->sock_rfd->fd = c->sock_wfd->fd = c->remote_fd.fd;
        sock_open_rd = sock_open_wr = 1;
        rd_sock_closed = 0;
        call_home_mode = 1;
    }   


    s_log ( LOG_NOTICE, "%s: CLI: %p c->sock_rfd->fd:%d  c->sock_wfd->fd:%d   sock_open_rd:%d sock_open_wr:%d ", __func__, c, c->sock_rfd->fd, c->sock_wfd->fd,  sock_open_rd, sock_open_wr);

    c->sock_ptr = c->ssl_ptr = 0;

    do
    {
        /* main loop of client data transfer */
        /****************************** initialize *_wants_* */
        read_wants_read |= ! ( SSL_get_shutdown ( c->ssl ) & SSL_RECEIVED_SHUTDOWN )  && c->ssl_ptr < BUFFSIZE && !read_wants_write;
        write_wants_write |= ! ( SSL_get_shutdown ( c->ssl ) & SSL_SENT_SHUTDOWN )  && c->sock_ptr && !write_wants_read;


        /****************************** setup c->fds structure */
        s_poll_init ( c->fds ); /* initialize the structure */
        /* for plain socket open data strem = open file descriptor */

        /* make sure to add each open socket to receive exceptions! */
        if ( sock_open_rd ) /* only poll if the read file descriptor is open */
            s_poll_add ( c->fds, c->sock_rfd->fd, c->sock_ptr < BUFFSIZE, 0 );

        if ( sock_open_wr ) /* only poll if the write file descriptor is open */
            s_poll_add ( c->fds, c->sock_wfd->fd, 0, c->ssl_ptr > 0 );

        /* poll SSL file descriptors unless SSL shutdown was completed */
        if ( SSL_get_shutdown ( c->ssl ) != ( SSL_SENT_SHUTDOWN | SSL_RECEIVED_SHUTDOWN ) )
        {
            s_poll_add ( c->fds, c->ssl_rfd->fd, read_wants_read || write_wants_read || shutdown_wants_read, 0 );
            s_poll_add ( c->fds, c->ssl_wfd->fd, 0, read_wants_write || write_wants_write || shutdown_wants_write );
        }


        /****************************** wait for an event */
        err = s_poll_wait ( c->fds,
                            ( sock_open_rd && /* both peers open */
                              ! ( SSL_get_shutdown ( c->ssl ) &SSL_RECEIVED_SHUTDOWN ) ) ||
                            c->ssl_ptr /* data buffered to write to socket */ ||
                            c->sock_ptr /* data buffered to write to SSL */ ?
                            c->opt->timeout_idle : c->opt->timeout_close, 0 );

        switch ( err )
        {
        case -1:
            sockerror ( "transfer: s_poll_wait" );
            longjmp ( c->err, 1 );


        case 0: /* timeout */

            if ( ( sock_open_rd &&
                    ! ( SSL_get_shutdown ( c->ssl ) &SSL_RECEIVED_SHUTDOWN ) ) ||
                    c->ssl_ptr || c->sock_ptr )
            {
                s_log ( LOG_INFO, "%s: transfer: s_poll_wait:"
                        " TIMEOUTidle exceeded: sending reset", __func__ );
                longjmp ( c->err, 1 );
            }
            else
            {
                /* already closing connection */
                s_log ( LOG_ERR, "%s: transfer: s_poll_wait:"
                        " TIMEOUTclose exceeded: closing", __func__ );
                return; /* OK */
            }
        }


        /****************************** retrieve results from c->fds */
        sock_can_rd = s_poll_canread ( c->fds, c->sock_rfd->fd );
        sock_can_wr = s_poll_canwrite ( c->fds, c->sock_wfd->fd );
        ssl_can_rd = s_poll_canread ( c->fds, c->ssl_rfd->fd );
        ssl_can_wr = s_poll_canwrite ( c->fds, c->ssl_wfd->fd );


        /****************************** checks for internal failures */
        /* please report any internal errors to stunnel-users mailing list */
        if ( ! ( sock_can_rd || sock_can_wr || ssl_can_rd || ssl_can_wr ) )
        {
            s_log ( LOG_ERR, "%s: INTERNAL ERROR: "
                    "s_poll_wait returned %d, but no descriptor is ready", __func__, err );
            longjmp ( c->err, 1 );
        }

        if ( c->reneg_state == RENEG_DETECTED && !c->opt->option.renegotiation )
        {
            s_log ( LOG_ERR, "%s: Aborting due to renegotiation request", __func__ );
            longjmp ( c->err, 1 );
        }

        /****************************** send SSL close_notify alert */
        if ( shutdown_wants_read || shutdown_wants_write )
        {
            num = SSL_shutdown ( c->ssl ); /* send close_notify alert */

            if ( num < 0 ) /* -1 - not completed */
                err = SSL_get_error ( c->ssl, ( int ) num );
            else /* 0 or 1 - success */
                err = SSL_ERROR_NONE;

            switch ( err )
            {
            case SSL_ERROR_NONE: /* the shutdown was successfully completed */
                s_log ( LOG_INFO, "%s: SSL_shutdown successfully sent close_notify alert", __func__ );
                shutdown_wants_read = shutdown_wants_write = 0;
                break;

            case SSL_ERROR_SYSCALL: /* socket error */

                if ( parse_socket_error ( c, "SSL_shutdown" ) )
                    break; /* a non-critical error: retry */

                SSL_set_shutdown ( c->ssl, SSL_SENT_SHUTDOWN | SSL_RECEIVED_SHUTDOWN );
                shutdown_wants_read = shutdown_wants_write = 0;
                break;

            case SSL_ERROR_WANT_WRITE:
                s_log ( LOG_DEBUG, "%s: SSL_shutdown returned WANT_WRITE: retrying", __func__ );
                shutdown_wants_read = 0;
                shutdown_wants_write = 1;
                break;

            case SSL_ERROR_WANT_READ:
                s_log ( LOG_DEBUG, "%s: SSL_shutdown returned WANT_READ: retrying", __func__ );
                shutdown_wants_read = 1;
                shutdown_wants_write = 0;
                break;

            case SSL_ERROR_SSL: /* SSL error */
                sslerror ( "SSL_shutdown" );
                longjmp ( c->err, 1 );

            default:
                s_log ( LOG_ERR, "%s: SSL_shutdown/SSL_get_error returned %d", __func__, err );
                longjmp ( c->err, 1 );
            }
        }


        /****************************** write to socket */
        if ( sock_open_wr && sock_can_wr )
        {

            num = writesocket ( c->sock_wfd->fd, c->ssl_buff, c->ssl_ptr );

            switch ( num )
            {

            case -1: /* error */

                if ( parse_socket_error ( c, "writesocket" ) )
                    break; /* a non-critical error: retry */

                sock_open_rd = sock_open_wr = 0;
                break;

            default:
                s_log ( LOG_DEBUG, "\n" );
                s_log ( LOG_DEBUG, "%s Write Socket: %d(FD)  write ptr :%p ssl_ptr:%lu num:%lu\n", __func__, c->sock_wfd->fd, c->ssl_buff, c->ssl_ptr, num );
                hexDebugDump ( c->ssl_buff, num );

                memmove ( c->ssl_buff, c->ssl_buff + num, c->ssl_ptr - ( size_t ) num );
                c->ssl_ptr -= ( size_t ) num;
                memset ( c->ssl_buff + c->ssl_ptr, 0, ( size_t ) num ); /* paranoia */
                c->sock_bytes += ( size_t ) num;
                watchdog = 0; /* reset watchdog */
            }
        }



        /****************************** read from socket */
        if ( sock_open_rd && sock_can_rd && !rd_sock_closed)
        {
            num = readsocket ( c->sock_rfd->fd, c->sock_buff + c->sock_ptr, BUFFSIZE - ( c->sock_ptr ) );

            switch ( num )
            {

            case -1:

                if ( parse_socket_error ( c, "readsocket" ) )
                    break; /* a non-critical error: retry */

                sock_open_rd = sock_open_wr = 0;
                break;

            case 0: /* close */

                s_log ( LOG_INFO, "%s: Read socket closed (readsocket)", __func__ );
                //sock_open_rd = 0;
                rd_sock_closed = 1;
				if( !c->opt->option.client )
	                c->remote_fd.fd = -1;
                break;

            default:
                s_log ( LOG_DEBUG, "\n %s(): Read from socket: %d(FD) Read ptr:%p sock_ptr:%lu num:%lu\n", __func__, c->sock_rfd->fd, c->sock_buff + c->sock_ptr, BUFFSIZE - c->sock_ptr, num );
                hexDebugDump ( c->sock_buff, num );
                c->sock_ptr += ( size_t ) num;
                watchdog = 0; /* reset watchdog */
				if( !c->opt->option.client )
                	rd_sock_closed = 0;
            }
        }


        /****************************** update *_wants_* based on new *_ptr */
        /* this update is also required for SSL_pending() to be used */
        read_wants_read     |= ! ( SSL_get_shutdown ( c->ssl ) &SSL_RECEIVED_SHUTDOWN ) && c->ssl_ptr < BUFFSIZE && !read_wants_write;
        write_wants_write   |= ! ( SSL_get_shutdown ( c->ssl ) &SSL_SENT_SHUTDOWN ) && c->sock_ptr && !write_wants_read;

        if ( !memcmp(c->sock_buff, NC_V10_START_MSG, 5) || !memcmp(c->sock_buff,NC_V11_START_MSG, 2))
        {
            uint8_t *ptr =  c->sock_buff;
            add_usc_header(&write_usc, c, c->sock_ptr, USC_OP_DATA, USC_SEC_TRANS_TLS); 
            s_log ( LOG_DEBUG, "Adding version:%d usc_op:%d app_id:%d session:%d sec:%d payload:%d \n",
                    write_usc.usc_version,
                    write_usc.usc_op,
                    ntohs ( write_usc.app_id ),
                    ntohs ( write_usc.app_session ),
                    write_usc.sec_trans,
                    ntohs ( write_usc.payload_length ) );                    
            ptr = adjust_usc_header_payload(&ptr, c->sock_ptr);
            memcpy ( c->sock_buff, &write_usc, sizeof(usc_header) );
            c->sock_ptr += sizeof(usc_header);
        }

        /****************************** write to SSL */
        if ( ( write_wants_read && ssl_can_rd ) ||  ( write_wants_write && ssl_can_wr ) )
        {
            write_wants_read = 0;
            write_wants_write = 0;
            num = SSL_write ( c->ssl, c->sock_buff, ( int ) ( c->sock_ptr ) );

            switch ( err = SSL_get_error ( c->ssl, ( int ) num ) )
            {
            case SSL_ERROR_NONE:

                if ( num == 0 )
                    s_log ( LOG_DEBUG, "%s: SSL_write returned 0", __func__ );
                else
                {
                    s_log ( LOG_DEBUG, "\n" );
                    s_log ( LOG_DEBUG, "%s(): write to SSL: write ptr:%p sock_ptr:%lu num:%lu\n", __func__, c->sock_buff, c->sock_ptr, num );
                    hexDebugDump ( c->sock_buff, num );
                }

                memmove ( c->sock_buff, c->sock_buff + num, c->sock_ptr - ( size_t ) num );
                c->sock_ptr -= ( size_t ) num;
                memset ( c->sock_buff + c->sock_ptr, 0, ( size_t ) num ); /* paranoia */
                c->ssl_bytes += ( size_t ) num;
                watchdog = 0; /* reset watchdog */
                break;

            case SSL_ERROR_WANT_WRITE: /* buffered data? */
                s_log ( LOG_DEBUG, "%s: SSL_write returned WANT_WRITE: retrying", __func__ );
                write_wants_write = 1;
                break;

            case SSL_ERROR_WANT_READ:
                s_log ( LOG_DEBUG, "%s: SSL_write returned WANT_READ: retrying", __func__ );
                write_wants_read = 1;
                break;

            case SSL_ERROR_WANT_X509_LOOKUP:
                s_log ( LOG_DEBUG,
                        "%s: SSL_write returned WANT_X509_LOOKUP: retrying", __func__ );
                break;

            case SSL_ERROR_SYSCALL: /* socket error */

                if ( num && parse_socket_error ( c, "SSL_write" ) )
                    break; /* a non-critical error: retry */

                /* EOF -> buggy (e.g. Microsoft) peer:
                 * SSL socket closed without close_notify alert */
                if ( c->sock_ptr )
                {
                    /* TODO: what about buffered data? */
                    s_log ( LOG_ERR,
                            "%s: SSL socket closed (SSL_write) with %ld unsent byte(s)", __func__,
                            c->sock_ptr );
                    longjmp ( c->err, 1 ); /* reset the socket */
                }

                s_log ( LOG_INFO, "%s: SSL socket closed (SSL_write)", __func__ );
                SSL_set_shutdown ( c->ssl, SSL_SENT_SHUTDOWN | SSL_RECEIVED_SHUTDOWN );
                break;

            case SSL_ERROR_ZERO_RETURN: /* close_notify alert received */
                s_log ( LOG_INFO, "%s: SSL closed (SSL_write)", __func__ );

                if ( SSL_version ( c->ssl ) == SSL2_VERSION )
                    SSL_set_shutdown ( c->ssl, SSL_SENT_SHUTDOWN | SSL_RECEIVED_SHUTDOWN );

                break;

            case SSL_ERROR_SSL:
                sslerror ( "SSL_write" );
                longjmp ( c->err, 1 );

            default:
                s_log ( LOG_ERR, "%s: SSL_write/SSL_get_error returned %d", __func__, err );
                longjmp ( c->err, 1 );
            }
        }



        /****************************** read from SSL */
        if ( ( read_wants_read && ( ssl_can_rd || SSL_pending ( c->ssl ) ) ) ||
                /* it may be possible to read some pending data after
                 * writesocket() above made some room in c->ssl_buff */
                ( read_wants_write && ssl_can_wr ) )
        {
            read_wants_read = 0;
            read_wants_write = 0;

            num = SSL_read ( c->ssl, c->ssl_buff + c->ssl_ptr, ( int ) ( BUFFSIZE - c->ssl_ptr ) );

            switch ( err = SSL_get_error ( c->ssl, ( int ) num ) )
            {

            case SSL_ERROR_NONE:

                if ( num == 0 )
                    s_log ( LOG_DEBUG, "%s: SSL_read returned 0", __func__ );
                else
                {
                    s_log ( LOG_DEBUG, "\n" );
                    s_log ( LOG_DEBUG, "%s(): Read from SSL: read ptr:%p c->ssl_ptr:%lu ssl_ptr:%lu num:%lu\n", __func__, c->ssl_buff + c->ssl_ptr, c->ssl_ptr, ( int ) ( BUFFSIZE - c->ssl_ptr ), num );
                    hexDebugDump ( c->ssl_buff, num );
                    //hexDebugDump((c->ssl_buff + c->ssl_ptr + num-strlen(NC_V10_END_MSG)),strlen(NC_V10_END_MSG));
                }

                c->ssl_ptr += ( size_t ) num;
                watchdog = 0; /* reset watchdog */
                break;

            case SSL_ERROR_WANT_WRITE:
                s_log ( LOG_DEBUG, "%s: SSL_read returned WANT_WRITE: retrying", __func__ );
                read_wants_write = 1;
                break;

            case SSL_ERROR_WANT_READ: /* is it possible? */
                s_log ( LOG_DEBUG, "%s: SSL_read returned WANT_READ: retrying", __func__ );
                read_wants_read = 1;
                break;

            case SSL_ERROR_WANT_X509_LOOKUP:
                s_log ( LOG_DEBUG, "%s: SSL_read returned WANT_X509_LOOKUP: retrying", __func__ );
                break;

            case SSL_ERROR_SYSCALL:

                if ( num && parse_socket_error ( c, "SSL_read" ) )
                    break; /* a non-critical error: retry */

                /* EOF -> buggy (e.g. Microsoft) peer:
                 * SSL socket closed without close_notify alert */
                if ( c->sock_ptr || write_wants_write )
                {
                    s_log ( LOG_ERR, "%s SSL socket closed (SSL_read) with %ld unsent byte(s)", __func__, c->sock_ptr );
                    longjmp ( c->err, 1 ); /* reset the socket */
                }

                s_log ( LOG_INFO, "%s: SSL socket closed (SSL_read)", __func__ );
                SSL_set_shutdown ( c->ssl,
                                   SSL_SENT_SHUTDOWN | SSL_RECEIVED_SHUTDOWN );
                break;

            case SSL_ERROR_ZERO_RETURN: /* close_notify alert received */
                s_log ( LOG_INFO, "%s: SSL closed (SSL_read)", __func__ );

                if ( SSL_version ( c->ssl ) == SSL2_VERSION )
                    SSL_set_shutdown ( c->ssl,  SSL_SENT_SHUTDOWN | SSL_RECEIVED_SHUTDOWN );

                break;

            case SSL_ERROR_SSL:
                sslerror ( "SSL_read" );
                longjmp ( c->err, 1 );

            default:
                s_log ( LOG_ERR, "%s: SSL_read/SSL_get_error returned %d", __func__, err );
                longjmp ( c->err, 1 );
            }


        }

        /* checking for USC header for version */
        read_usc = c->ssl_buff;
        if (( read_usc->usc_version == USC_VERSION ) && num > sizeof ( usc_header ))
        {   
            int fd; 
            usc_size = sizeof ( usc_header );        
            tmp_ptr = c->ssl_buff;
            tmp_ptr += usc_size;
            c->ssl_ptr -= usc_size;        
        
            if( ntohs(read_usc->app_id) != TLS_NETCONF_PORT )
            {
                   uint8_t err_buf[8];
                   int8_t *ptr = err_buf;
                   prepare_error_handling( ptr,read_usc->app_id, read_usc->app_session, get_last_socket_error() ); 
                   s_log ( LOG_DEBUG, "%s: Server is not Reachable, sending Error USC Message ", __func__ );
                   hexDebugDump ( err_buf, sizeof ( usc_header ));
                   num = SSL_write ( c->ssl, err_buf, sizeof ( usc_header )); 
            }
            else
            {

                if ( c->remote_fd.fd == -1 )
                {               
                    fd = connect_local ( c );  
                    /* In case Server is not available or reachable USC handling with USC Error Message */
                    if( fd < 0 )
                    {
                        uint8_t err_buf[8];
                        int8_t *ptr = err_buf;
                        prepare_error_handling( ptr,read_usc->app_id, read_usc->app_session, get_last_socket_error() ); 
                        s_log ( LOG_DEBUG, "%s: Server is not Reachable, sending Error USC Message ", __func__ );
                        hexDebugDump ( err_buf, sizeof ( usc_header ));
                        num = SSL_write ( c->ssl, err_buf, sizeof ( usc_header )); 
                    }
                    else
                    {  
    	                c->remote_fd.fd = fd;
                        c->remote_fd.is_socket = 1; /* always! */
                        s_log ( LOG_DEBUG, "%s: Remote socket (FD=%d) initialized", __func__,  c->remote_fd.fd );

                        if ( set_socket_options ( c->remote_fd.fd, 2 ) )
                            s_log ( LOG_WARNING, "%s: Failed to set remote socket options", __func__ );

                        c->sock_rfd->fd = c->sock_wfd->fd = c->remote_fd.fd;
                        sock_open_rd = sock_open_wr = 1;
                        rd_sock_closed = 0;

                        /* storing relevent details to locate fd mapping*/
                        c->usc.usc_version = read_usc->usc_version;
                        c->usc.usc_op = read_usc->usc_op;
                        c->usc.app_id = ntohs ( read_usc->app_id );
                        c->usc.app_session = ntohs ( read_usc->app_session );
                        c->usc.sec_trans = read_usc->sec_trans;
                        c->usc.payload_length = ntohs ( read_usc->payload_length);

                        s_log ( LOG_DEBUG, "Assigned version:%d usc_op:%d app_id:%d \
                                session:%d sec:%d payload:%d \n",
                            c->usc.usc_version,
                            c->usc.usc_op,
                            c->usc.app_id,
                            c->usc.app_session,
                            c->usc.sec_trans,
                            c->usc.payload_length );
                    }
    	        }
                else 
                {
                     if( parse_usc_header(c->ssl_buff, c) )
                     {
                            
                        
                     }
                }    
             }
             memmove ( c->ssl_buff, tmp_ptr, c->ssl_ptr );
            
        }


        /****************************** check for hangup conditions */
        /* http://marc.info/?l=linux-man&m=128002066306087 */
        /* readsocket() must be the last sock_rfd operation before FIONREAD */
       #if 0 
        if ( sock_open_rd && s_poll_rdhup ( c->fds, c->sock_rfd->fd ) &&  ( ioctlsocket ( c->sock_rfd->fd, FIONREAD, &bytes ) || !bytes ) )
        {
            s_log ( LOG_INFO, "%s: Read socket closed (read hangup)", __func__ );
            sock_open_rd = 0;
        }
        #endif
        if ( sock_open_wr && s_poll_hup ( c->fds, c->sock_wfd->fd ) )
        {
            if ( c->ssl_ptr )
            {
                s_log ( LOG_ERR,
                        "%s: Write socket closed (write hangup) with %ld unsent byte(s)", __func__,
                        c->ssl_ptr );
                longjmp ( c->err, 1 ); /* reset the socket */
            }

            s_log ( LOG_INFO, "%s: Write socket closed (write hangup)", __func__ );
            sock_open_wr = 0;
        }

        /* SSL_read() must be the last ssl_rfd operation before FIONREAD */
        if ( ! ( SSL_get_shutdown ( c->ssl ) &SSL_RECEIVED_SHUTDOWN ) &&
                s_poll_rdhup ( c->fds, c->ssl_rfd->fd ) &&
                ( ioctlsocket ( c->ssl_rfd->fd, FIONREAD, &bytes ) || !bytes ) )
        {
            /* hangup -> buggy (e.g. Microsoft) peer:
             * SSL socket closed without close_notify alert */
            s_log ( LOG_INFO, "%s: SSL socket closed (read hangup)", __func__ );
            SSL_set_shutdown ( c->ssl,
                               SSL_get_shutdown ( c->ssl ) | SSL_RECEIVED_SHUTDOWN );
        }

        if ( ! ( SSL_get_shutdown ( c->ssl ) &SSL_SENT_SHUTDOWN ) &&
                s_poll_hup ( c->fds, c->ssl_wfd->fd ) )
        {

            if ( c->sock_ptr || write_wants_write )
            {
                s_log ( LOG_ERR,
                        "%s: SSL socket closed (write hangup) with %ld unsent byte(s)", __func__,
                        c->sock_ptr );
                longjmp ( c->err, 1 ); /* reset the socket */
            }

            s_log ( LOG_INFO, "%s: SSL socket closed (write hangup)", __func__ );
            SSL_set_shutdown ( c->ssl,
                               SSL_get_shutdown ( c->ssl ) | SSL_SENT_SHUTDOWN );
        }

        /****************************** check write shutdown conditions */
        if ( sock_open_wr && SSL_get_shutdown ( c->ssl ) &SSL_RECEIVED_SHUTDOWN && !c->ssl_ptr )
        {
            sock_open_wr = 0; /* no further write allowed */

            if ( !c->sock_wfd->is_socket )
            {
                s_log ( LOG_DEBUG, "%s: Closing the file descriptor", __func__ );
                sock_open_rd = 0; /* file descriptor is ready to be closed */
            }
            else if ( !shutdown ( c->sock_wfd->fd, SHUT_WR ) )
            {
                /* send TCP FIN */
                s_log ( LOG_DEBUG, "%s: Sent socket write shutdown", __func__ );
            }
            else
            {
                s_log ( LOG_DEBUG, "%s: Failed to send socket write shutdown", __func__ );
                sock_open_rd = 0; /* file descriptor is ready to be closed */
            }
        }

        if ( ! ( SSL_get_shutdown ( c->ssl ) &SSL_SENT_SHUTDOWN ) && !sock_open_rd && !c->sock_ptr && !write_wants_write )
        {
            if ( SSL_version ( c->ssl ) != SSL2_VERSION )
            {
                s_log ( LOG_DEBUG, "%s: Sending close_notify alert", __func__ );
				if( !c->opt->option.client )
                	shutdown_wants_write = 1;
            }
            else
            {
                /* no alerts in SSLv2, including the close_notify alert */
                s_log ( LOG_DEBUG, "%s: Closing SSLv2 socket", __func__ );

                if ( c->ssl_rfd->is_socket )
                    shutdown ( c->ssl_rfd->fd, SHUT_RD ); /* notify the kernel */

                if ( c->ssl_wfd->is_socket )
                    shutdown ( c->ssl_wfd->fd, SHUT_WR ); /* send TCP FIN */

                /* notify the OpenSSL library */
                SSL_set_shutdown ( c->ssl, SSL_SENT_SHUTDOWN | SSL_RECEIVED_SHUTDOWN );
            }
        }


        
    }
    while ( sock_open_wr || ! ( SSL_get_shutdown ( c->ssl ) &SSL_SENT_SHUTDOWN ) ||
            shutdown_wants_read || shutdown_wants_write );
}

/* returns 0 on close and 1 on non-critical errors */
int parse_socket_error ( CLI *c, const char *text )
{
    switch ( get_last_socket_error() )
    {
        /* http://tangentsoft.net/wskfaq/articles/bsd-compatibility.html */
    case 0: /* close on read, or close on write on WIN32 */
#ifndef USE_WIN32
    case EPIPE: /* close on write on Unix */
#endif
    case S_ECONNABORTED:
        s_log ( LOG_INFO, "%s: %s: Socket is closed", __func__, text );
        return 0;
    case S_EINTR:
        s_log ( LOG_DEBUG, "%s: %s: Interrupted by a signal: retrying", __func__, text );
        return 1;
    case S_EWOULDBLOCK:
        s_log ( LOG_NOTICE, "%s: %s: Would block: retrying", __func__, text );
        sleep ( 1 ); /* Microsoft bug KB177346 */
        return 1;
#if S_EAGAIN!=S_EWOULDBLOCK
    case S_EAGAIN:
        s_log ( LOG_DEBUG,
                "%s: Temporary lack of resources: retrying",  __func__, text );
        return 1;
#endif
#ifdef USE_WIN32
    case S_ECONNRESET:

        /* dying "exec" processes on Win32 cause reset instead of close */
        if ( c->opt->option.program )
        {
            s_log ( LOG_INFO, "%s: %s: Socket is closed (exec)", __func__, text );
            return 0;
        }

#endif
    default:
        sockerror ( text );
        longjmp ( c->err, 1 );
        return -1; /* some C compilers require a return value */

    }
}



void print_cipher ( CLI *c )
{
    /* print negotiated cipher */
    SSL_CIPHER *cipher;
#ifndef OPENSSL_NO_COMP
    const COMP_METHOD *compression, *expansion;
#endif

    if ( global_options.debug_level < LOG_INFO ) /* performance optimization */
        return;

    cipher = ( SSL_CIPHER * ) SSL_get_current_cipher ( c->ssl );
    s_log ( LOG_INFO, "%s: Negotiated %s ciphersuite %s (%d-bit encryption)", __func__,
            SSL_get_version ( c->ssl ), SSL_CIPHER_get_name ( cipher ),
            SSL_CIPHER_get_bits ( cipher, NULL ) );

#ifndef OPENSSL_NO_COMP
    compression = SSL_get_current_compression ( c->ssl );
    expansion = SSL_get_current_expansion ( c->ssl );
    s_log ( compression || expansion ? LOG_INFO : LOG_DEBUG,
            "%s: Compression: %s, expansion: %s", __func__,
            compression ? SSL_COMP_get_name ( compression ) : "null",
            expansion ? SSL_COMP_get_name ( expansion ) : "null" );
#endif
}

NOEXPORT void auth_user ( CLI *c, char *accepted_address )
{
#ifndef _WIN32_WCE
    struct servent *s_ent;    /* structure for getservbyname */
#endif
    SOCKADDR_UNION ident;     /* IDENT socket name */
    char *line, *type, *system, *user;

    if ( !c->opt->username )
        return; /* -u option not specified */

#ifdef HAVE_STRUCT_SOCKADDR_UN

    if ( c->peer_addr.sa.sa_family == AF_UNIX )
    {
        s_log ( LOG_INFO, "%s: IDENT not supported on Unix sockets", __func__ );
        return;
    }

#endif

    c->fd = s_socket ( c->peer_addr.sa.sa_family, SOCK_STREAM,
                       0, 1, "socket (auth_user)" );

    if ( c->fd < 0 )
        longjmp ( c->err, 1 );

    memcpy ( &ident, &c->peer_addr, c->peer_addr_len );
#ifndef _WIN32_WCE
    s_ent = getservbyname ( "auth", "tcp" );

    if ( s_ent )
    {
        ident.in.sin_port = ( uint16_t ) s_ent->s_port;
    }
    else

#endif
    {
        s_log ( LOG_WARNING, "%s: Unknown service 'auth': using default 113", __func__ );
        ident.in.sin_port = htons ( 113 );
    }

    if ( s_connect ( c, &ident, addr_len ( &ident ) ) )
        longjmp ( c->err, 1 );

    s_log ( LOG_DEBUG, "%s: IDENT server connected", __func__ );
    fd_printf ( c, c->fd, "%u , %u",
                ntohs ( c->peer_addr.in.sin_port ),
                ntohs ( c->opt->local_addr.in.sin_port ) );
    line = fd_getline ( c, c->fd );
    closesocket ( c->fd );
    c->fd = -1; /* avoid double close on cleanup */
    type = strchr ( line, ':' );

    if ( !type )
    {
        s_log ( LOG_ERR, "%s: Malformed IDENT response",  __func__ );
        str_free ( line );
        longjmp ( c->err, 1 );
    }

    *type++ = '\0';
    system = strchr ( type, ':' );

    if ( !system )
    {
        s_log ( LOG_ERR, "%s: Malformed IDENT response",  __func__ );
        str_free ( line );
        longjmp ( c->err, 1 );
    }

    *system++ = '\0';

    if ( strcmp ( type, " USERID " ) )
    {
        s_log ( LOG_ERR, "%s: Incorrect INETD response type",  __func__ );
        str_free ( line );
        longjmp ( c->err, 1 );
    }

    user = strchr ( system, ':' );

    if ( !user )
    {
        s_log ( LOG_ERR, "%s: Malformed IDENT response",  __func__ );
        str_free ( line );
        longjmp ( c->err, 1 );
    }

    *user++ = '\0';

    while ( *user == ' ' ) /* skip leading spaces */
        ++user;

    if ( strcmp ( user, c->opt->username ) )
    {
        s_log ( LOG_WARNING, "%s: Connection from %s REFUSED by IDENT (user \"%s\")", __func__,
                accepted_address, user );
        str_free ( line );
        longjmp ( c->err, 1 );
    }

    s_log ( LOG_INFO, "%s: IDENT authentication passed", __func__ );
    str_free ( line );
}

#if defined(_WIN32_WCE) || defined(__vms)

int connect_local ( CLI *c )
{
    /* spawn local process */
    s_log ( LOG_ERR, "%s: Local mode is not supported on this platform", __func__ );
    longjmp ( c->err, 1 );
    return -1; /* some C compilers require a return value */
}

#elif defined(USE_WIN32)

int connect_local ( CLI *c )
{
    /* spawn local process */
    int fd[2];
    STARTUPINFO si;
    PROCESS_INFORMATION pi;
    LPTSTR name, args;

    if ( make_sockets ( fd ) )
        longjmp ( c->err, 1 );

    memset ( &si, 0, sizeof si );
    si.cb = sizeof si;
    si.dwFlags = STARTF_USESHOWWINDOW | STARTF_USESTDHANDLES;
    si.wShowWindow = SW_HIDE;
    si.hStdInput = si.hStdOutput = si.hStdError = ( HANDLE ) fd[1];
    memset ( &pi, 0, sizeof pi );

    name = str2tstr ( c->opt->execname );
    args = str2tstr ( c->opt->execargs );
    CreateProcess ( name, args, NULL, NULL, TRUE, 0, NULL, NULL, &si, &pi );
    str_free ( name );
    str_free ( args );

    closesocket ( fd[1] );
    CloseHandle ( pi.hProcess );
    CloseHandle ( pi.hThread );
    return fd[0];
}

#else /* standard Unix version */

int connect_local ( CLI *c )
{
    /* spawn local process */
    char *name, host[40], port[6];
    int fd[2], pid;
    X509 *peer;
    unsigned char *digest;
    unsigned int dig_len, i;
    STACK_OF ( GENERAL_NAME ) *san_names;
    GENERAL_NAME *san_name;
    ASN1_OCTET_STRING *ip;
#ifdef HAVE_PTHREAD_SIGMASK
    sigset_t newmask;
#endif

    if ( c->opt->option.pty )
    {
        char tty[64];

        if ( pty_allocate ( fd, fd + 1, tty ) )
            longjmp ( c->err, 1 );

        s_log ( LOG_DEBUG, "%s: TTY=%s allocated", __func__, tty );
    }
    else if ( make_sockets ( fd ) )
        longjmp ( c->err, 1 );

    pid = fork();
    c->pid = ( unsigned long ) pid;

    switch ( pid )
    {

    case -1:    /* error */
        closesocket ( fd[0] );
        closesocket ( fd[1] );
        ioerror ( "fork" );
        longjmp ( c->err, 1 );

    case  0:    /* child */
        closesocket ( fd[0] );
        set_nonblock ( fd[1], 0 ); /* switch back to blocking mode */
        /* dup2() does not copy FD_CLOEXEC flag */
        dup2 ( fd[1], 0 );
        dup2 ( fd[1], 1 );

        if ( !global_options.option.foreground )
            dup2 ( fd[1], 2 );

        closesocket ( fd[1] ); /* not really needed due to FD_CLOEXEC */

        if ( !getnameinfo ( &c->peer_addr.sa, c->peer_addr_len,
                            host, 40, port, 6, NI_NUMERICHOST | NI_NUMERICSERV ) )
        {
            /* just don't set these variables if getnameinfo() fails */
            putenv ( str_printf ( "REMOTE_HOST=%s", host ) );
            putenv ( str_printf ( "REMOTE_PORT=%s", port ) );

            if ( c->opt->option.transparent_src )
            {
#ifndef LIBDIR
#define LIBDIR "."
#endif
#ifdef MACH64
                putenv ( "LD_PRELOAD_32=" LIBDIR "/libstunnel.so" );
                putenv ( "LD_PRELOAD_64=" LIBDIR "/" MACH64 "/libstunnel.so" );
#elif __osf /* for Tru64 _RLD_LIST is used instead */
                putenv ( "_RLD_LIST=" LIBDIR "/libstunnel.so:DEFAULT" );
#else
                putenv ( "LD_PRELOAD=" LIBDIR "/libstunnel.so" );
#endif
            }
        }

        if ( c->ssl )
        {
            peer = SSL_get_peer_certificate ( c->ssl );

            if ( peer )
            {
                name = X509_NAME2text ( X509_get_subject_name ( peer ) );
                putenv ( str_printf ( "SSL_CLIENT_DN=%s", name ) );
                name = X509_NAME2text ( X509_get_issuer_name ( peer ) );
                putenv ( str_printf ( "SSL_CLIENT_I_DN=%s", name ) );

                /* calculate peer fingerprints using MD5 and SHA algorithms */
                dig_len = 64;
                digest = malloc ( dig_len );
                X509_digest ( peer, EVP_md5(), digest, &dig_len );
                name = str_printf ( "SSL_CLIENT_MD5=%02x:%02x:%02x:%02x", digest[0], digest[1], digest[2], digest[3] );

                for ( i = 4; i < dig_len; i += 4 )
                {
                    name = str_printf ( "%s:%02x:%02x:%02x:%02x", name, digest[i], digest[i + 1], digest[i + 2], digest[i + 3] );
                }

                putenv ( name );

                X509_digest ( peer, EVP_sha1(), digest, &dig_len );
                name = str_printf ( "SSL_CLIENT_SHA1=%02x:%02x:%02x:%02x", digest[0], digest[1], digest[2], digest[3] );

                for ( i = 4; i < dig_len; i += 4 )
                {
                    name = str_printf ( "%s:%02x:%02x:%02x:%02x", name, digest[i], digest[i + 1], digest[i + 2], digest[i + 3] );
                }

                putenv ( name );

                X509_digest ( peer, EVP_sha224(), digest, &dig_len );
                name = str_printf ( "SSL_CLIENT_SHA224=%02x:%02x:%02x:%02x", digest[0], digest[1], digest[2], digest[3] );

                for ( i = 4; i < dig_len; i += 4 )
                {
                    name = str_printf ( "%s:%02x:%02x:%02x:%02x", name, digest[i], digest[i + 1], digest[i + 2], digest[i + 3] );
                }

                putenv ( name );

                X509_digest ( peer, EVP_sha256(), digest, &dig_len );
                name = str_printf ( "SSL_CLIENT_SHA256=%02x:%02x:%02x:%02x", digest[0], digest[1], digest[2], digest[3] );

                for ( i = 4; i < dig_len; i += 4 )
                {
                    name = str_printf ( "%s:%02x:%02x:%02x:%02x", name, digest[i], digest[i + 1], digest[i + 2], digest[i + 3] );
                }

                putenv ( name );

                X509_digest ( peer, EVP_sha384(), digest, &dig_len );
                name = str_printf ( "SSL_CLIENT_SHA384=%02x:%02x:%02x:%02x", digest[0], digest[1], digest[2], digest[3] );

                for ( i = 4; i < dig_len; i += 4 )
                {
                    name = str_printf ( "%s:%02x:%02x:%02x:%02x", name, digest[i], digest[i + 1], digest[i + 2], digest[i + 3] );
                }

                putenv ( name );

                X509_digest ( peer, EVP_sha512(), digest, &dig_len );
                name = str_printf ( "SSL_CLIENT_SHA512=%02x:%02x:%02x:%02x", digest[0], digest[1], digest[2], digest[3] );

                for ( i = 4; i < dig_len; i += 4 )
                {
                    name = str_printf ( "%s:%02x:%02x:%02x:%02x", name, digest[i], digest[i + 1], digest[i + 2], digest[i + 3] );
                }

                putenv ( name );
                free ( digest );

                /* retrieve subjectAltName's rfc822Name (email), dNSName and iPAddress values */
                san_names = X509_get_ext_d2i ( peer, NID_subject_alt_name, NULL, NULL );

                if ( san_names != NULL )
                {
                    name = str_printf ( "SSL_CLIENT_SAN=" );

                    for ( i = 0; i < ( unsigned ) sk_GENERAL_NAME_num ( san_names ); ++i )
                    {
                        san_name = sk_GENERAL_NAME_value ( san_names, i );

                        if ( san_name->type == GEN_EMAIL || san_name->type == GEN_DNS || san_name->type == GEN_IPADD )
                        {
                            if ( san_name->type == GEN_EMAIL )
                            {
                                name = str_printf ( "%s/EMAIL=%s", name, ( char* ) ASN1_STRING_data ( san_name->d.rfc822Name ) );
                            }

                            if ( san_name->type == GEN_DNS )
                            {
                                name = str_printf ( "%s/DNS=%s", name, ( char* ) ASN1_STRING_data ( san_name->d.dNSName ) );
                            }

                            if ( san_name->type == GEN_IPADD )
                            {
                                ip = san_name->d.iPAddress;

                                if ( ip->length == 4 )
                                {
                                    name = str_printf ( "%s/IP=%d.%d.%d.%d", name, ip->data[0], ip->data[1], ip->data[2], ip->data[3] );
                                }
                                else if ( ip->length == 16 )
                                {
                                    name = str_printf ( "%s/IP=%02x%02x%02x%02x%02x%02x%02x%02x%02x%02x%02x%02x%02x%02x%02x%02x",
                                                        name, ip->data[0], ip->data[1], ip->data[2], ip->data[3], ip->data[4], ip->data[5],
                                                        ip->data[6], ip->data[7], ip->data[8], ip->data[9], ip->data[10], ip->data[11], ip->data[12],
                                                        ip->data[13], ip->data[14], ip->data[15] );
                                }
                            }
                        }
                    }

                    if ( strlen ( name ) > 15 )
                    {
                        putenv ( name );
                    }

                    sk_GENERAL_NAME_pop_free ( san_names, GENERAL_NAME_free );
                }

                X509_free ( peer );
            }
        }

#ifdef HAVE_PTHREAD_SIGMASK
        sigemptyset ( &newmask );
        sigprocmask ( SIG_SETMASK, &newmask, NULL );
#endif
        signal ( SIGCHLD, SIG_DFL );
        signal ( SIGHUP, SIG_DFL );
        signal ( SIGUSR1, SIG_DFL );
        signal ( SIGPIPE, SIG_DFL );
        signal ( SIGTERM, SIG_DFL );
        signal ( SIGQUIT, SIG_DFL );
        signal ( SIGINT, SIG_DFL );
        if(!c->opt->execname) 
        {        
           c->opt->execname = NETOPEER_AGENT;
           c->opt->execname = NETOPEER_ARGS;
        }        
        execvp ( c->opt->execname, c->opt->execargs );
        ioerror ( c->opt->execname ); /* execvp failed */
        _exit ( 1 );
    default: /* parent */
        s_log ( LOG_INFO, "%s: Local mode child started (PID=%lu)",  __func__, c->pid);
        closesocket ( fd[1] );
        return fd[0];
    }
}

#endif /* not USE_WIN32 or __vms */

/* connect remote host */
int connect_remote ( CLI *c )
{
    int fd;
    unsigned ind_start, ind_try, ind_cur;
    
    s_log ( LOG_NOTICE, "%s: CLI: %p", __func__, c );


    setup_connect_addr ( c );
    ind_start = *c->connect_addr.rr_ptr;

    /* the race condition here can be safely ignored */
    if ( c->opt->failover == FAILOVER_RR )
        *c->connect_addr.rr_ptr = ( ind_start + 1 ) % c->connect_addr.num;

    /* try to connect each host from the list */
    for ( ind_try = 0; ind_try < c->connect_addr.num; ind_try++ )
    {
        ind_cur = ( ind_start + ind_try ) % c->connect_addr.num;
        c->fd = s_socket ( c->connect_addr.addr[ind_cur].sa.sa_family,
                           SOCK_STREAM, 0, 1, "remote socket" );

        if ( c->fd < 0 )
            longjmp ( c->err, 1 );

        local_bind ( c ); /* explicit local bind or transparent proxy */

        if ( s_connect ( c, &c->connect_addr.addr[ind_cur],
                         addr_len ( &c->connect_addr.addr[ind_cur] ) ) )
        {
            closesocket ( c->fd );
            c->fd = -1;
            continue; /* next IP */
        }

        print_bound_address ( c );
        fd = c->fd;
        c->fd = -1;
        return fd; /* success! */
    }

    longjmp ( c->err, 1 );
    return -1; /* some C compilers require a return value */
}

void setup_connect_addr ( CLI *c )
{
#ifdef SO_ORIGINAL_DST
    socklen_t addrlen = sizeof ( SOCKADDR_UNION );
#endif /* SO_ORIGINAL_DST */

    /* check if the address was already set by the verify callback,
     * or a dynamic protocol
     * implemented protocols: CONNECT, SOCKS */
    if ( c->connect_addr.num )
        return;

#ifdef SO_ORIGINAL_DST

    if ( c->opt->option.transparent_dst )
    {
        c->connect_addr.num = 1;
        c->connect_addr.addr = str_alloc ( sizeof ( SOCKADDR_UNION ) );

        if ( getsockopt ( c->local_rfd.fd, SOL_IP, SO_ORIGINAL_DST,
                          c->connect_addr.addr, &addrlen ) )
        {
            sockerror ( "setsockopt SO_ORIGINAL_DST" );
            longjmp ( c->err, 1 );
        }

        return;
    }

#endif /* SO_ORIGINAL_DST */

    if ( c->opt->connect_addr.num )
    {
        /* pre-resolved addresses */
        addrlist_dup ( &c->connect_addr, &c->opt->connect_addr );
        return;
    }

    /* delayed lookup */
    if ( namelist2addrlist ( &c->connect_addr,
                             c->opt->connect_list, DEFAULT_LOOPBACK ) )
        return;

    s_log ( LOG_ERR, "%s: No host resolved",  __func__ );
    longjmp ( c->err, 1 );
}

void local_bind ( CLI *c )
{
#ifndef USE_WIN32
    int on;

    on = 1;
#endif

    if ( !c->bind_addr )
        return;

#if defined(USE_WIN32)
    /* do nothing */
#elif defined(__linux__)

    /* non-local bind on Linux */
    if ( c->opt->option.transparent_src )
    {
        if ( setsockopt ( c->fd, SOL_IP, IP_TRANSPARENT, &on, sizeof on ) )
        {
            sockerror ( "setsockopt IP_TRANSPARENT" );

            if ( setsockopt ( c->fd, SOL_IP, IP_FREEBIND, &on, sizeof on ) )
                sockerror ( "setsockopt IP_FREEBIND" );
            else
                s_log ( LOG_INFO, "%s: IP_FREEBIND socket option set", __func__ );
        }
        else
            s_log ( LOG_INFO, "%s: IP_TRANSPARENT socket option set",  __func__ );

        /* ignore the error to retain Linux 2.2 compatibility */
        /* the error will be handled by bind(), anyway */
    }

#elif defined(IP_BINDANY) && defined(IPV6_BINDANY)

    /* non-local bind on FreeBSD */
    if ( c->opt->option.transparent_src )
    {
        if ( c->bind_addr->sa.sa_family == AF_INET )
        {
            /* IPv4 */
            if ( setsockopt ( c->fd, IPPROTO_IP, IP_BINDANY, &on, sizeof on ) )
            {
                sockerror ( "setsockopt IP_BINDANY" );
                longjmp ( c->err, 1 );
            }
        }
        else
        {
            /* IPv6 */
            if ( setsockopt ( c->fd, IPPROTO_IPV6, IPV6_BINDANY, &on, sizeof on ) )
            {
                sockerror ( "setsockopt IPV6_BINDANY" );
                longjmp ( c->err, 1 );
            }
        }
    }

#else

    /* unsupported platform */
    if ( c->opt->option.transparent_src )
    {
        s_log ( LOG_ERR, "%s: Transparent proxy in remote mode is not supported"
                " on this platform",  __func__ );
        longjmp ( c->err, 1 );
    }

#endif

    if ( ntohs ( c->bind_addr->in.sin_port ) >= 1024 )
    {
        /* security check */
        /* this is currently only possible with transparent_src */
        if ( !bind ( c->fd, &c->bind_addr->sa, addr_len ( c->bind_addr ) ) )
        {
            s_log ( LOG_INFO, "%s: local_bind succeeded on the original port",  __func__ );
            return; /* success */
        }

        if ( get_last_socket_error() != S_EADDRINUSE )
        {
            sockerror ( "local_bind (original port)" );
            longjmp ( c->err, 1 );
        }
    }

    c->bind_addr->in.sin_port = htons ( 0 ); /* retry with ephemeral port */

    if ( !bind ( c->fd, &c->bind_addr->sa, addr_len ( c->bind_addr ) ) )
    {
        s_log ( LOG_INFO, "%s: local_bind succeeded on an ephemeral port",  __func__ );
        return; /* success */
    }

    sockerror ( "local_bind (ephemeral port)" );
    longjmp ( c->err, 1 );
}

void print_bound_address ( CLI *c )
{
    char *txt;
    SOCKADDR_UNION addr;
    socklen_t addrlen = sizeof addr;

    if ( global_options.debug_level < LOG_NOTICE ) /* performance optimization */
        return;

    memset ( &addr, 0, addrlen );

    if ( getsockname ( c->fd, ( struct sockaddr * ) &addr, &addrlen ) )
    {
        sockerror ( "getsockname" );
        return;
    }

    txt = s_ntop ( &addr, addrlen );
    s_log ( LOG_NOTICE, "Service [%s] connected remote server from %s", __func__,
            c->opt->servname, txt );
    str_free ( txt );
}

void reset ( int fd, char *txt )
{
    /* set lingering on a socket */
    struct linger l;

    l.l_onoff = 1;
    l.l_linger = 0;

    if ( setsockopt ( fd, SOL_SOCKET, SO_LINGER, ( void * ) &l, sizeof l ) )
        log_error ( LOG_DEBUG, get_last_socket_error(), txt );
}

/* end of client.c */
