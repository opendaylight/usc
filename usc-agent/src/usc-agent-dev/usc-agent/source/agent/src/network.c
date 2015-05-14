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

#if defined(_WIN32) || defined(_WIN32_WCE)
/* bypass automatic index bound checks in the FD_SET() macro */
#define FD_SETSIZE 1000000
#endif

#include "common.h"
#include "prototypes.h"


/* #define DEBUG_UCONTEXT */

NOEXPORT void s_poll_realloc ( s_poll_set * );
NOEXPORT int get_socket_error ( const int );

/**************************************** s_poll functions */

#ifdef USE_POLL

s_poll_set *s_poll_alloc()
{
    /* it needs to be filled with zeros */
    return str_alloc ( sizeof ( s_poll_set ) );
}

void s_poll_free ( s_poll_set *fds )
{
    if ( fds )
    {
        if ( fds->ufds )
            str_free ( fds->ufds );

        str_free ( fds );
    }
}

void s_poll_init ( s_poll_set *fds )
{
    fds->nfds = 0;
    fds->allocated = 4; /* prealloc 4 file desciptors */
    s_poll_realloc ( fds );
}

void s_poll_add ( s_poll_set *fds, int fd, int rd, int wr )
{
    unsigned i;

    for ( i = 0; i < fds->nfds && fds->ufds[i].fd != fd; i++ )
        ;

    if ( i == fds->nfds )
    {
        if ( i == fds->allocated )
        {
            fds->allocated = i + 1;
            s_poll_realloc ( fds );
        }

        fds->ufds[i].fd = fd;
        fds->ufds[i].events = 0;
        fds->nfds++;
    }

    if ( rd )
    {
        fds->ufds[i].events |= POLLIN;
#ifdef POLLRDHUP
        fds->ufds[i].events |= POLLRDHUP;
#endif
    }

    if ( wr )
        fds->ufds[i].events |= POLLOUT;
}

int s_poll_canread ( s_poll_set *fds, int fd )
{
    unsigned i;

    for ( i = 0; i < fds->nfds; i++ )
        if ( fds->ufds[i].fd == fd )
            return fds->ufds[i].revents & POLLIN;

    return 0; /* not listed in fds */
}

int s_poll_canwrite ( s_poll_set *fds, int fd )
{
    unsigned i;

    for ( i = 0; i < fds->nfds; i++ )
        if ( fds->ufds[i].fd == fd )
            return fds->ufds[i].revents & POLLOUT;

    return 0; /* not listed in fds */
}

/* best doc: http://lxr.free-electrons.com/source/net/ipv4/tcp.c#L456 */

int s_poll_hup ( s_poll_set *fds, int fd )
{
    unsigned i;

    for ( i = 0; i < fds->nfds; i++ )
        if ( fds->ufds[i].fd == fd )
            return fds->ufds[i].revents & POLLHUP; /* read and write closed */

    return 0; /* not listed in fds */
}

int s_poll_rdhup ( s_poll_set *fds, int fd )
{
    unsigned i;

    for ( i = 0; i < fds->nfds; i++ )
        if ( fds->ufds[i].fd == fd )
#ifdef POLLRDHUP
            return fds->ufds[i].revents & POLLRDHUP; /* read closed */

#else
            return fds->ufds[i].revents & POLLHUP; /* read and write closed */
#endif
    return 0; /* not listed in fds */
}

NOEXPORT void s_poll_realloc ( s_poll_set *fds )
{
    fds->ufds = str_realloc ( fds->ufds, fds->allocated * sizeof ( struct pollfd ) );
}

#ifdef USE_UCONTEXT

/* move ready contexts from waiting queue to ready queue */
NOEXPORT void scan_waiting_queue ( void )
{
    int retval;
    CONTEXT *context, *prev;
    int min_timeout;
    unsigned nfds, i;
    time_t now;
    static unsigned max_nfds = 0;
    static struct pollfd *ufds = NULL;

    time ( &now );
    /* count file descriptors */
    min_timeout = -1;
    nfds = 0;

    for ( context = waiting_head; context; context = context->next )
    {
        nfds += context->fds->nfds;

        if ( context->finish >= 0 ) /* finite time */
            if ( min_timeout < 0 || min_timeout > context->finish - now )
                min_timeout = context->finish - now < 0 ? 0 : context->finish - now;
    }

    /* setup ufds structure */
    if ( nfds > max_nfds ) /* need to allocate more memory */
    {
        ufds = str_realloc ( ufds, nfds * sizeof ( struct pollfd ) );
        max_nfds = nfds;
    }

    nfds = 0;

    for ( context = waiting_head; context; context = context->next )
        for ( i = 0; i < context->fds->nfds; i++ )
        {
            ufds[nfds].fd = context->fds->ufds[i].fd;
            ufds[nfds].events = context->fds->ufds[i].events;
            nfds++;
        }

#ifdef DEBUG_UCONTEXT
    s_log ( LOG_DEBUG, "%s: Waiting %d second(s) for %d file descriptor(s)", __func__,
            min_timeout, nfds );
#endif

    do   /* skip "Interrupted system call" errors */
    {
        retval = poll ( ufds, nfds, min_timeout < 0 ? -1 : 1000 * min_timeout );
    }
    while ( retval < 0 && get_last_socket_error() == S_EINTR );

    time ( &now );
    /* process the returned data */
    nfds = 0;
    prev = NULL; /* previous element of the waiting queue */
    context = waiting_head;

    while ( context )
    {
        context->ready = 0;

        /* count ready file descriptors in each context */
        for ( i = 0; i < context->fds->nfds; i++ )
        {
            context->fds->ufds[i].revents = ufds[nfds].revents;
#ifdef DEBUG_UCONTEXT
            s_log ( LOG_DEBUG, "%s: CONTEXT %ld, FD=%d,%s%s ->%s%s%s%s%s", __func__,
                    context->id, ufds[nfds].fd,
                    ufds[nfds].events & POLLIN ? " IN" : "",
                    ufds[nfds].events & POLLOUT ? " OUT" : "",
                    ufds[nfds].revents & POLLIN ? " IN" : "",
                    ufds[nfds].revents & POLLOUT ? " OUT" : "",
                    ufds[nfds].revents & POLLERR ? " ERR" : "",
                    ufds[nfds].revents & POLLHUP ? " HUP" : "",
                    ufds[nfds].revents & POLLNVAL ? " NVAL" : "" );
#endif

            if ( ufds[nfds].revents )
                context->ready++;

            nfds++;
        }

        if ( context->ready || ( context->finish >= 0 && context->finish <= now ) )
        {
            /* remove context from the waiting queue */
            if ( prev )
                prev->next = context->next;
            else
                waiting_head = context->next;

            if ( !context->next ) /* same as context==waiting_tail */
                waiting_tail = prev;

            /* append context context to the ready queue */
            context->next = NULL;

            if ( ready_tail )
                ready_tail->next = context;

            ready_tail = context;

            if ( !ready_head )
                ready_head = context;
        }
        else     /* leave the context context in the waiting queue */
        {
            prev = context;
        }

        context = prev ? prev->next : waiting_head;
    }
}

int s_poll_wait ( s_poll_set *fds, int sec, int msec )
{
    CONTEXT *context; /* current context */
    static CONTEXT *to_free = NULL; /* delayed memory deallocation */

    /* FIXME: msec parameter is currently ignored with UCONTEXT threads */
    ( void ) msec; /* skip warning about unused parameter */

    /* remove the current context from ready queue */
    context = ready_head;
    ready_head = ready_head->next;

    if ( !ready_head ) /* the queue is empty */
        ready_tail = NULL;

    /* it it safe to s_log() after new ready_head is set */

    /* it's illegal to deallocate the stack of the current context */
    if ( to_free ) /* a delayed deallocation is scheduled */
    {
#ifdef DEBUG_UCONTEXT
        s_log ( LOG_DEBUG, "%s: Releasing context %ld",  __func__, to_free->id );
#endif
        str_free ( to_free->stack );
        str_free ( to_free );
        to_free = NULL;
    }

    /* manage the current thread */
    if ( fds ) /* something to wait for -> swap the context */
    {
        context->fds = fds; /* set file descriptors to wait for */
        context->finish = sec < 0 ? -1 : time ( NULL ) + sec;

        /* append the current context to the waiting queue */
        context->next = NULL;

        if ( waiting_tail )
            waiting_tail->next = context;

        waiting_tail = context;

        if ( !waiting_head )
            waiting_head = context;
    }
    else     /* nothing to wait for -> drop the context */
    {
        to_free = context; /* schedule for delayed deallocation */
    }

    while ( !ready_head ) /* wait until there is a thread to switch to */
        scan_waiting_queue();

    /* switch threads */
    if ( fds ) /* swap the current context */
    {
        if ( context->id != ready_head->id )
        {
#ifdef DEBUG_UCONTEXT
            s_log ( LOG_DEBUG, "%s: Context swap: %ld -> %ld", __func__,
                    context->id, ready_head->id );
#endif
            swapcontext ( &context->context, &ready_head->context );
#ifdef DEBUG_UCONTEXT
            s_log ( LOG_DEBUG, "%s: Current context: %ld",  __func__, ready_head->id );
#endif
        }

        return ready_head->ready;
    }
    else     /* drop the current context */
    {
#ifdef DEBUG_UCONTEXT
        s_log ( LOG_DEBUG, "%s: Context set: %ld (dropped) -> %ld", __func__,
                context->id, ready_head->id );
#endif
        setcontext ( &ready_head->context );
        ioerror ( "setcontext" ); /* should not ever happen */
        return 0;
    }
}

#else /* USE_UCONTEXT */

int s_poll_wait ( s_poll_set *fds, int sec, int msec )
{
    int retval;

    do   /* skip "Interrupted system call" errors */
    {
        retval = poll ( fds->ufds, fds->nfds, sec < 0 ? -1 : 1000 * sec + msec );
    }
    while ( retval < 0 && get_last_socket_error() == S_EINTR );

    return retval;
}

#endif /* USE_UCONTEXT */

#else /* select */

s_poll_set *s_poll_alloc()
{
    /* it needs to be filled with zeros */
    return str_alloc ( sizeof ( s_poll_set ) );
}

void s_poll_free ( s_poll_set *fds )
{
    if ( fds )
    {
        if ( fds->irfds )
            str_free ( fds->irfds );

        if ( fds->iwfds )
            str_free ( fds->iwfds );

        if ( fds->ixfds )
            str_free ( fds->ixfds );

        if ( fds->orfds )
            str_free ( fds->orfds );

        if ( fds->owfds )
            str_free ( fds->owfds );

        if ( fds->oxfds )
            str_free ( fds->oxfds );

        str_free ( fds );
    }
}

void s_poll_init ( s_poll_set *fds )
{
#ifdef USE_WIN32
    fds->allocated = 4; /* prealloc 4 file desciptors */
#endif
    s_poll_realloc ( fds );
    FD_ZERO ( fds->irfds );
    FD_ZERO ( fds->iwfds );
    FD_ZERO ( fds->ixfds );
    fds->max = 0; /* no file descriptors */
}

void s_poll_add ( s_poll_set *fds, int fd, int rd, int wr )
{
#ifdef USE_WIN32

    /* fds->ixfds contains union of fds->irfds and fds->iwfds */
    if ( fds->ixfds->fd_count >= fds->allocated )
    {
        fds->allocated = fds->ixfds->fd_count + 1;
        s_poll_realloc ( fds );
    }

#endif

    if ( rd )
        FD_SET ( ( unsigned ) fd, fds->irfds );

    if ( wr )
        FD_SET ( ( unsigned ) fd, fds->iwfds );

    /* always expect errors (and the Spanish Inquisition) */
    FD_SET ( ( unsigned ) fd, fds->ixfds );

    if ( fd > fds->max )
        fds->max = fd;
}

int s_poll_canread ( s_poll_set *fds, int fd )
{
    return FD_ISSET ( fd, fds->orfds );
}

int s_poll_canwrite ( s_poll_set *fds, int fd )
{
    return FD_ISSET ( fd, fds->owfds );
}

int s_poll_hup ( s_poll_set *fds, int fd )
{
    ( void ) fds; /* skip warning about unused parameter */
    ( void ) fd; /* skip warning about unused parameter */
    return 0; /* FIXME: how to detect HUP condition with select()? */
}

int s_poll_rdhup ( s_poll_set *fds, int fd )
{
    ( void ) fds; /* skip warning about unused parameter */
    ( void ) fd; /* skip warning about unused parameter */
    return 0; /* FIXME: how to detect RDHUP condition with select()? */
}

#ifdef USE_WIN32
#define FD_SIZE(fds) (sizeof(u_int)+(fds)->allocated*sizeof(SOCKET))
#else
#define FD_SIZE(fds) (sizeof(fd_set))
#endif

int s_poll_wait ( s_poll_set *fds, int sec, int msec )
{
    int retval;
    struct timeval tv, *tv_ptr;

    do   /* skip "Interrupted system call" errors */
    {
        memcpy ( fds->orfds, fds->irfds, FD_SIZE ( fds ) );
        memcpy ( fds->owfds, fds->iwfds, FD_SIZE ( fds ) );
        memcpy ( fds->oxfds, fds->ixfds, FD_SIZE ( fds ) );

        if ( sec < 0 ) /* infinite timeout */
        {
            tv_ptr = NULL;
        }
        else
        {
            tv.tv_sec = sec;
            tv.tv_usec = 1000 * msec;
            tv_ptr = &tv;
        }

        retval = select ( fds->max + 1, fds->orfds, fds->owfds, fds->oxfds, tv_ptr );
    }
    while ( retval < 0 && get_last_socket_error() == S_EINTR );

    return retval;
}

NOEXPORT void s_poll_realloc ( s_poll_set *fds )
{
    fds->irfds = str_realloc ( fds->irfds, FD_SIZE ( fds ) );
    fds->iwfds = str_realloc ( fds->iwfds, FD_SIZE ( fds ) );
    fds->ixfds = str_realloc ( fds->ixfds, FD_SIZE ( fds ) );
    fds->orfds = str_realloc ( fds->orfds, FD_SIZE ( fds ) );
    fds->owfds = str_realloc ( fds->owfds, FD_SIZE ( fds ) );
    fds->oxfds = str_realloc ( fds->oxfds, FD_SIZE ( fds ) );
}

#endif /* USE_POLL */

/**************************************** fd management */

int set_socket_options ( int s, int type )
{
    SOCK_OPT *ptr;
    extern SOCK_OPT sock_opts[];
    static char *type_str[3] = {"accept", "local", "remote"};
    socklen_t opt_size;
    int retval = 0; /* no error found */

    for ( ptr = sock_opts; ptr->opt_str; ptr++ )
    {
        if ( !ptr->opt_val[type] )
            continue; /* default */

        switch ( ptr->opt_type )
        {
        case TYPE_LINGER:
            opt_size = sizeof ( struct linger );
            break;
        case TYPE_TIMEVAL:
            opt_size = sizeof ( struct timeval );
            break;
        case TYPE_STRING:
            opt_size = ( socklen_t ) strlen ( ptr->opt_val[type]->c_val ) + 1;
            break;
        default:
            opt_size = sizeof ( int );
        }

        if ( setsockopt ( s, ptr->opt_level, ptr->opt_name,
                          ( void * ) ptr->opt_val[type], opt_size ) )
        {
            if ( get_last_socket_error() == S_EOPNOTSUPP )
            {
                /* most likely stdin/stdout or AF_UNIX socket */
                s_log ( LOG_DEBUG,
                        "Option %s not supported on %s socket", __func__,
                        ptr->opt_str, type_str[type] );
            }
            else
            {
                sockerror ( ptr->opt_str );
                retval = -1; /* failed to set this option */
            }
        }

#ifdef DEBUG_FD_ALLOC
        else
        {
            s_log ( LOG_DEBUG, "%s: Option %s set on %s socket", __func__,
                    ptr->opt_str, type_str[type] );
        }

#endif /* DEBUG_FD_ALLOC */
    }

    return retval; /* returns 0 when all options succeeded */
}

NOEXPORT int get_socket_error ( const int fd )
{
    int err;
    socklen_t optlen = sizeof err;

    if ( getsockopt ( fd, SOL_SOCKET, SO_ERROR, ( void * ) &err, &optlen ) )
        err = get_last_socket_error(); /* failed -> ask why */

    return err == S_ENOTSOCK ? 0 : err;
}

/**************************************** simulate blocking I/O */

int s_connect ( CLI *c, SOCKADDR_UNION *addr, socklen_t addrlen )
{
    int error;
    char *dst;

    dst = s_ntop ( addr, addrlen );
    s_log ( LOG_INFO, "%s: s_connect: connecting %s",  __func__, dst );

    if ( !connect ( c->fd, &addr->sa, addrlen ) )
    {
        s_log ( LOG_NOTICE, "%s: s_connect: connected %s",  __func__, dst );
        str_free ( dst );
        return 0; /* no error -> success (on some OSes over the loopback) */
    }

    error = get_last_socket_error();

    if ( error != S_EINPROGRESS && error != S_EWOULDBLOCK )
    {
        s_log ( LOG_ERR, "%s: s_connect: connect %s: %s (%d)", __func__,
                dst, s_strerror ( error ), error );
        str_free ( dst );
        return -1;
    }

    s_log ( LOG_DEBUG, "%s: s_connect: s_poll_wait %s: waiting %d seconds", __func__,
            dst, c->opt->timeout_connect );
    s_poll_init ( c->fds );
    s_poll_add ( c->fds, c->fd, 1, 1 );

    switch ( s_poll_wait ( c->fds, c->opt->timeout_connect, 0 ) )
    {
    case -1:
        error = get_last_socket_error();
        s_log ( LOG_ERR, "%s: s_connect: s_poll_wait %s: %s (%d)", __func__,
                dst, s_strerror ( error ), error );
        str_free ( dst );
        return -1;
    case 0:
        s_log ( LOG_ERR, "%s: s_connect: s_poll_wait %s:", __func__,
                " TIMEOUTconnect exceeded", dst );
        str_free ( dst );
        return -1;
    default:
        error = get_socket_error ( c->fd );

        if ( error )
        {
            s_log ( LOG_ERR, "%s: s_connect: connect %s: %s (%d)", __func__,
                    dst, s_strerror ( error ), error );
            str_free ( dst );
            return -1;
        }

        if ( s_poll_canwrite ( c->fds, c->fd ) )
        {
            s_log ( LOG_NOTICE, "%s: s_connect: connected %s",  __func__, dst );
            str_free ( dst );
            return 0; /* success */
        }

        s_log ( LOG_ERR, "%s: s_connect: s_poll_wait %s: internal error", __func__,
                dst );
        str_free ( dst );
        return -1;
    }

    return -1; /* should not be possible */
}

void s_write ( CLI *c, int fd, const void *buf, size_t len )
{
    /* simulate a blocking write */
    uint8_t *ptr = ( uint8_t * ) buf;
    ssize_t num;

    while ( len > 0 )
    {
        s_poll_init ( c->fds );
        s_poll_add ( c->fds, fd, 0, 1 ); /* write */

        switch ( s_poll_wait ( c->fds, c->opt->timeout_busy, 0 ) )
        {
        case -1:
            sockerror ( "s_write: s_poll_wait" );
            longjmp ( c->err, 1 ); /* error */
        case 0:
            s_log ( LOG_INFO, "%s: s_write: s_poll_wait:"
                    " TIMEOUTbusy exceeded: sending reset",  __func__ );
            longjmp ( c->err, 1 ); /* timeout */
        case 1:
            break; /* OK */
        default:
            s_log ( LOG_ERR, "%s: s_write: s_poll_wait: unknown result", __func__ );
            longjmp ( c->err, 1 ); /* error */
        }

        num = writesocket ( fd, ( void * ) ptr, len );

        if ( num == -1 ) /* error */
        {
            sockerror ( "writesocket (s_write)" );
            longjmp ( c->err, 1 );
        }

        ptr += ( size_t ) num;
        len -= ( size_t ) num;
    }
}

void s_read ( CLI *c, int fd, void *ptr, size_t len )
{
    /* simulate a blocking read */
    ssize_t num;

    while ( len > 0 )
    {
        s_poll_init ( c->fds );
        s_poll_add ( c->fds, fd, 1, 0 ); /* read */

        switch ( s_poll_wait ( c->fds, c->opt->timeout_busy, 0 ) )
        {
        case -1:
            sockerror ( "s_read: s_poll_wait" );
            longjmp ( c->err, 1 ); /* error */
        case 0:
            s_log ( LOG_INFO, "%s: s_read: s_poll_wait:"
                    " TIMEOUTbusy exceeded: sending reset",  __func__ );
            longjmp ( c->err, 1 ); /* timeout */
        case 1:
            break; /* OK */
        default:
            s_log ( LOG_ERR, "%s: s_read: s_poll_wait: unknown result", __func__ );
            longjmp ( c->err, 1 ); /* error */
        }

        num = readsocket ( fd, ptr, len );

        switch ( num )
        {
        case -1: /* error */
            sockerror ( "readsocket (s_read)" );
            longjmp ( c->err, 1 );
        case 0: /* EOF */
            s_log ( LOG_ERR, "%s: Unexpected socket close (s_read)",  __func__ );
            longjmp ( c->err, 1 );
        }

        ptr = ( uint8_t * ) ptr + num;
        len -= ( size_t ) num;
    }
}

void fd_putline ( CLI *c, int fd, const char *line )
{
    char *tmpline;
    const char crlf[] = "\r\n";
    size_t len;

    tmpline = str_printf ( "%s%s", line, crlf );
    len = strlen ( tmpline );
    s_write ( c, fd, tmpline, len );
    tmpline[len - 2] = '\0'; /* remove CRLF */
    s_log ( LOG_DEBUG, "%s:  -> %s",  __func__, tmpline );
    str_free ( tmpline );
}

char *fd_getline ( CLI *c, int fd )
{
    char *line;
    size_t ptr = 0, allocated = 32;

    line = str_alloc ( allocated );

    for ( ;; )
    {
        if ( ptr > 65536 ) /* >64KB --> DoS protection */
        {
            s_log ( LOG_ERR, "%s: fd_getline: Line too long",  __func__ );
            str_free ( line );
            longjmp ( c->err, 1 );
        }

        if ( allocated < ptr + 1 )
        {
            allocated *= 2;
            line = str_realloc ( line, allocated );
        }

        s_read ( c, fd, line + ptr, 1 );

        if ( line[ptr] == '\r' )
            continue;

        if ( line[ptr] == '\n' )
            break;

        if ( line[ptr] == '\0' )
            break;

        ++ptr;
    }

    line[ptr] = '\0';
    s_log ( LOG_DEBUG, "%s:  <- %s",  __func__, line );
    return line;
}

void fd_printf ( CLI *c, int fd, const char *format, ... )
{
    va_list ap;
    char *line;

    va_start ( ap, format );
    line = str_vprintf ( format, ap );
    va_end ( ap );

    if ( !line )
    {
        s_log ( LOG_ERR, "%s: fd_printf: str_vprintf failed",  __func__ );
        longjmp ( c->err, 1 );
    }

    fd_putline ( c, fd, line );
    str_free ( line );
}

void s_ssl_write ( CLI *c, const void *buf, int len )
{
    /* simulate a blocking SSL_write */
    uint8_t *ptr = ( uint8_t * ) buf;
    int num;

    while ( len > 0 )
    {
        s_poll_init ( c->fds );
        s_poll_add ( c->fds, c->ssl_wfd->fd, 0, 1 ); /* write */

        switch ( s_poll_wait ( c->fds, c->opt->timeout_busy, 0 ) )
        {
        case -1:
            sockerror ( "s_write: s_poll_wait" );
            longjmp ( c->err, 1 ); /* error */
        case 0:
            s_log ( LOG_INFO, "%s: s_write: s_poll_wait:"
                    " TIMEOUTbusy exceeded: sending reset",  __func__ );
            longjmp ( c->err, 1 ); /* timeout */
        case 1:
            break; /* OK */
        default:
            s_log ( LOG_ERR, "%s: s_write: s_poll_wait: unknown result",  __func__ );
            longjmp ( c->err, 1 ); /* error */
        }

        num = SSL_write ( c->ssl, ( void * ) ptr, len );

        if ( num == -1 ) /* error */
        {
            sockerror ( "SSL_write (s_ssl_write)" );
            longjmp ( c->err, 1 );
        }

        ptr += num;
        len -= num;
    }
}

void s_ssl_read ( CLI *c, void *ptr, int len )
{
    /* simulate a blocking SSL_read */
    int num;

    while ( len > 0 )
    {
        if ( !SSL_pending ( c->ssl ) )
        {
            s_poll_init ( c->fds );
            s_poll_add ( c->fds, c->ssl_rfd->fd, 1, 0 ); /* read */

            switch ( s_poll_wait ( c->fds, c->opt->timeout_busy, 0 ) )
            {
            case -1:
                sockerror ( "s_read: s_poll_wait" );
                longjmp ( c->err, 1 ); /* error */
            case 0:
                s_log ( LOG_INFO, "%s: s_read: s_poll_wait:"
                        " TIMEOUTbusy exceeded: sending reset",  __func__ );
                longjmp ( c->err, 1 ); /* timeout */
            case 1:
                break; /* OK */
            default:
                s_log ( LOG_ERR, "%s: s_read: s_poll_wait: unknown result",  __func__ );
                longjmp ( c->err, 1 ); /* error */
            }
        }

        num = SSL_read ( c->ssl, ptr, len );

        switch ( num )
        {
        case -1: /* error */
            sockerror ( "SSL_read (s_ssl_read)" );
            longjmp ( c->err, 1 );
        case 0: /* EOF */
            s_log ( LOG_ERR, "%s: Unexpected socket close (s_ssl_read)",  __func__ );
            longjmp ( c->err, 1 );
        }

        ptr = ( uint8_t * ) ptr + num;
        len -= num;
    }
}

char *ssl_getstring ( CLI *c ) /* get null-terminated string */
{
    char *line;
    size_t ptr = 0, allocated = 32;

    line = str_alloc ( allocated );

    for ( ;; )
    {
        if ( ptr > 65536 ) /* >64KB --> DoS protection */
        {
            s_log ( LOG_ERR, "%s: fd_getline: Line too long", __func__ );
            str_free ( line );
            longjmp ( c->err, 1 );
        }

        if ( allocated < ptr + 1 )
        {
            allocated *= 2;
            line = str_realloc ( line, allocated );
        }

        s_ssl_read ( c, line + ptr, 1 );

        if ( line[ptr] == '\0' )
            break;

        ++ptr;
    }

    return line;
}

#define INET_SOCKET_PAIR

int make_sockets ( int fd[2] ) /* make a pair of connected ipv4 sockets */
{
#ifdef INET_SOCKET_PAIR
    struct sockaddr_in addr;
    socklen_t addrlen;
    int s; /* temporary socket awaiting for connection */

    /* create two *blocking* sockets first */
    s = s_socket ( AF_INET, SOCK_STREAM, 0, 0, "make_sockets: s_socket#1" );

    if ( s < 0 )
    {
        return 1;
    }

    fd[1] = s_socket ( AF_INET, SOCK_STREAM, 0, 0, "make_sockets: s_socket#2" );

    if ( fd[1] < 0 )
    {
        closesocket ( s );
        return 1;
    }

    addrlen = sizeof addr;
    memset ( &addr, 0, addrlen );
    addr.sin_family = AF_INET;
    addr.sin_addr.s_addr = htonl ( INADDR_LOOPBACK );
    addr.sin_port = htons ( 0 ); /* dynamic port allocation */

    if ( bind ( s, ( struct sockaddr * ) &addr, addrlen ) )
        log_error ( LOG_DEBUG, get_last_socket_error(), "make_sockets: bind#1" );

    if ( bind ( fd[1], ( struct sockaddr * ) &addr, addrlen ) )
        log_error ( LOG_DEBUG, get_last_socket_error(), "make_sockets: bind#2" );

    if ( listen ( s, 1 ) )
    {
        sockerror ( "make_sockets: listen" );
        closesocket ( s );
        closesocket ( fd[1] );
        return 1;
    }

    if ( getsockname ( s, ( struct sockaddr * ) &addr, &addrlen ) )
    {
        sockerror ( "make_sockets: getsockname" );
        closesocket ( s );
        closesocket ( fd[1] );
        return 1;
    }

    if ( connect ( fd[1], ( struct sockaddr * ) &addr, addrlen ) )
    {
        sockerror ( "make_sockets: connect" );
        closesocket ( s );
        closesocket ( fd[1] );
        return 1;
    }

    fd[0] = s_accept ( s, ( struct sockaddr * ) &addr, &addrlen, 1,
                       "make_sockets: s_accept" );

    if ( fd[0] < 0 )
    {
        closesocket ( s );
        closesocket ( fd[1] );
        return 1;
    }

    closesocket ( s ); /* don't care about the result */
    set_nonblock ( fd[0], 1 );
    set_nonblock ( fd[1], 1 );
#else

    if ( s_socketpair ( AF_UNIX, SOCK_STREAM, 0, fd, 1, "make_sockets: socketpair" ) )
        return 1;

#endif
    return 0;
}

/* end of network.c */
