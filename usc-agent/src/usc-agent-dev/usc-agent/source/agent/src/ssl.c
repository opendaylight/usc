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


/* global OpenSSL initalization: compression, engine, entropy */
NOEXPORT int compression_init ( GLOBAL_OPTIONS * );
NOEXPORT int prng_init ( GLOBAL_OPTIONS * );
NOEXPORT int add_rand_file ( GLOBAL_OPTIONS *, const char * );

int cli_index, opt_index; /* to keep structure for callbacks */

int ssl_init ( void ) /* init SSL before parsing configuration file */
{
    SSL_load_error_strings();
    SSL_library_init();
    cli_index = SSL_get_ex_new_index ( 0, "cli index", NULL, NULL, NULL );
    opt_index = SSL_CTX_get_ex_new_index ( 0, "opt index", NULL, NULL, NULL );

    if ( cli_index < 0 || opt_index < 0 )
        return 1;

#ifndef OPENSSL_NO_ENGINE
    ENGINE_load_builtin_engines();
#endif
    return 0;
}

int ssl_configure ( GLOBAL_OPTIONS *global ) /* configure global SSL settings */
{
#ifdef USE_FIPS

    if ( FIPS_mode() != global->option.fips )
    {
        RAND_set_rand_method ( NULL ); /* reset RAND methods */

        if ( !FIPS_mode_set ( global->option.fips ) )
        {
            ERR_load_crypto_strings();
            sslerror ( "FIPS_mode_set" );
            return 1;
        }
    }

    s_log ( LOG_NOTICE, "%s: FIPS mode %s",  __func__,
            global->option.fips ? "enabled" : "disabled" );
#endif /* USE_FIPS */
#ifndef OPENSSL_NO_COMP

    if ( compression_init ( global ) )
        return 1;

#endif /* OPENSSL_NO_COMP */

    if ( prng_init ( global ) )
        return 1;

    s_log ( LOG_DEBUG, "%s: PRNG seeded successfully",  __func__ );
    return 0; /* SUCCESS */
}

#ifndef OPENSSL_NO_COMP
NOEXPORT int compression_init ( GLOBAL_OPTIONS *global )
{
    SSL_COMP *comp;
    STACK_OF ( SSL_COMP ) *ssl_comp_methods;

    ssl_comp_methods = SSL_COMP_get_compression_methods();

    if ( !ssl_comp_methods )
    {
        if ( global->compression == COMP_NONE )
        {
            s_log ( LOG_NOTICE, "%s: Failed to get compression methods",  __func__ );
            return 0; /* ignore */
        }
        else
        {
            s_log ( LOG_ERR, "%s: Failed to get compression methods",  __func__ );
            return 1;
        }
    }

    /* delete OpenSSL defaults (empty the SSL_COMP stack) */
    /* cannot use sk_SSL_COMP_pop_free, as it also destroys the stack itself */
    while ( sk_SSL_COMP_num ( ssl_comp_methods ) )
        OPENSSL_free ( sk_SSL_COMP_pop ( ssl_comp_methods ) );

    if ( global->compression == COMP_NONE )
    {
        s_log ( LOG_DEBUG, "%s: Compression disabled",  __func__ );
        return 0; /* success */
    }

    /* insert RFC 1951 (DEFLATE) algorithm */
    if ( SSLeay() >= 0x00908051L ) /* 0.9.8e-beta1 */
    {
        /* only allow DEFLATE with OpenSSL 0.9.8 or later
           with openssl #1468 zlib memory leak fixed */
        comp = ( SSL_COMP * ) OPENSSL_malloc ( sizeof ( SSL_COMP ) );

        if ( !comp )
        {
            s_log ( LOG_ERR, "%s: OPENSSL_malloc filed",  __func__ );
            return 1;
        }

        comp->id = 1; /* RFC 1951 */
        comp->method = COMP_zlib();

        if ( !comp->method || comp->method->type == NID_undef )
        {
            OPENSSL_free ( comp );
            s_log ( LOG_ERR, "%s: Failed to initialize compression method",  __func__ );
            return 1;
        }

        comp->name = ( char * ) ( comp->method->name );
        sk_SSL_COMP_push ( ssl_comp_methods, comp );
    }

    /* also insert one of obsolete (ZLIB/RLE) algorithms */
    comp = ( SSL_COMP * ) OPENSSL_malloc ( sizeof ( SSL_COMP ) );

    if ( !comp )
    {
        s_log ( LOG_ERR, "%s: OPENSSL_malloc filed",  __func__ );
        return 1;
    }

    if ( global->compression == COMP_ZLIB )
    {
        comp->id = 0xe0; /* 224 - within private range (193 to 255) */
        comp->method = COMP_zlib();
    }
    else if ( global->compression == COMP_RLE )
    {
        comp->id = 0xe1; /* 225 - within private range (193 to 255) */
        comp->method = COMP_rle();
    }
    else
    {
        s_log ( LOG_INFO, "%s: Compression enabled: %d algorithm(s)",  __func__,
                sk_SSL_COMP_num ( ssl_comp_methods ) );
        OPENSSL_free ( comp );
        return 0;
    }

    if ( !comp->method || comp->method->type == NID_undef )
    {
        OPENSSL_free ( comp );
        s_log ( LOG_ERR, "%s: Failed to initialize compression method",  __func__ );
        return 1;
    }

    comp->name = ( char * ) ( comp->method->name );
    sk_SSL_COMP_push ( ssl_comp_methods, comp );
    s_log ( LOG_INFO, "%s: Compression enabled: %d algorithm(s)",  __func__,
            sk_SSL_COMP_num ( ssl_comp_methods ) );
    return 0; /* success */
}
#endif /* OPENSSL_NO_COMP */

NOEXPORT int prng_init ( GLOBAL_OPTIONS *global )
{
    int totbytes = 0;
    char filename[256];
#ifndef USE_WIN32
    int bytes;
#endif

    filename[0] = '\0';

    /* if they specify a rand file on the command line we
       assume that they really do want it, so try it first */
    if ( global->rand_file )
    {
        totbytes += add_rand_file ( global, global->rand_file );

        if ( RAND_status() )
            return 0; /* success */
    }

    /* try the $RANDFILE or $HOME/.rnd files */
    RAND_file_name ( filename, 256 );

    if ( filename[0] )
    {
        totbytes += add_rand_file ( global, filename );

        if ( RAND_status() )
            return 0; /* success */
    }

#ifdef RANDOM_FILE
    totbytes += add_rand_file ( global, RANDOM_FILE );

    if ( RAND_status() )
        return 0; /* success */

#endif

#ifdef USE_WIN32
    RAND_screen();

    if ( RAND_status() )
    {
        s_log ( LOG_DEBUG, "%s: Seeded PRNG with RAND_screen",  __func__ );
        return 0; /* success */
    }

    s_log ( LOG_DEBUG, "%s: RAND_screen failed to sufficiently seed PRNG",  __func__ );
#else

    if ( global->egd_sock )
    {
        if ( ( bytes = RAND_egd ( global->egd_sock ) ) == -1 )
        {
            s_log ( LOG_WARNING, "%s: EGD Socket %s failed",  __func__, global->egd_sock );
            bytes = 0;
        }
        else
        {
            totbytes += bytes;
            s_log ( LOG_DEBUG, "%s: Snagged %d random bytes from EGD Socket %s",  __func__,
                    bytes, global->egd_sock );
            return 0; /* OpenSSL always gets what it needs or fails,
                         so no need to check if seeded sufficiently */
        }
    }

    /* try the good-old default /dev/urandom, if available  */
    totbytes += add_rand_file ( global, "/dev/urandom" );

    if ( RAND_status() )
        return 0; /* success */

#endif /* USE_WIN32 */

    /* random file specified during configure */
    s_log ( LOG_ERR, "%s: PRNG seeded with %d bytes total",  __func__, totbytes );
    s_log ( LOG_ERR, "%s: PRNG was not seeded with enough random bytes",  __func__ );
    return 1; /* FAILED */
}

NOEXPORT int add_rand_file ( GLOBAL_OPTIONS *global, const char *filename )
{
    int readbytes;
    int writebytes;
    struct stat sb;

    if ( stat ( filename, &sb ) )
        return 0; /* could not stat() file --> return 0 bytes */

    if ( ( readbytes = RAND_load_file ( filename, global->random_bytes ) ) )
        s_log ( LOG_DEBUG, "%s: Snagged %d random bytes from %s",  __func__,
                readbytes, filename );
    else
        s_log ( LOG_INFO, "%s: Cannot retrieve any random data from %s",  __func__,
                filename );

    /* write new random data for future seeding if it's a regular file */
    if ( global->option.rand_write && ( sb.st_mode & S_IFREG ) )
    {
        writebytes = RAND_write_file ( filename );

        if ( writebytes == -1 )
            s_log ( LOG_WARNING, "%s: Failed to write strong random data to %s - "
                    "may be a permissions or seeding problem",  __func__, filename );
        else
            s_log ( LOG_DEBUG, "%s: Wrote %d new random bytes to %s",  __func__,
                    writebytes, filename );
    }

    return readbytes;
}

/* end of ssl.c */
