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

/**************************************** prototypes */

/* verify initialization */
NOEXPORT int load_file_lookup ( X509_STORE *, char * );
NOEXPORT int add_dir_lookup ( X509_STORE *, char * );

/* verify callback */
NOEXPORT int verify_callback ( int, X509_STORE_CTX * );
NOEXPORT int verify_checks ( int, X509_STORE_CTX * );
NOEXPORT int cert_check ( X509_STORE_CTX *, int );
NOEXPORT int cert_check_local ( X509_STORE_CTX * );
NOEXPORT int compare_pubkeys ( X509 *, X509 * );
NOEXPORT int crl_check ( X509_STORE_CTX * );
#ifndef OPENSSL_NO_OCSP
NOEXPORT int ocsp_check ( X509_STORE_CTX * );
NOEXPORT int ocsp_request ( CLI *, X509_STORE_CTX *, OCSP_CERTID *, char * );
NOEXPORT OCSP_RESPONSE *ocsp_get_response ( CLI *, OCSP_REQUEST *, char * );
#endif

/* utility functions */
NOEXPORT X509 *get_current_issuer ( X509_STORE_CTX * );
NOEXPORT void log_time ( const int, const char *, ASN1_TIME * );

/**************************************** verify initialization */

int verify_init ( SERVICE_OPTIONS *section )
{
    STACK_OF ( X509_NAME ) *ca_dn;
    char *ca_name;
    int i;

    if ( section->verify_level < 0 )
        return 0; /* OK - no certificate verification */

    if ( section->verify_level >= 2 && !section->ca_file && !section->ca_dir )
    {
        s_log ( LOG_ERR,
                "Either CApath or CAfile has to be used for authentication",  __func__ );
        return 1; /* FAILED */
    }

    section->revocation_store = X509_STORE_new();

    if ( !section->revocation_store )
    {
        sslerror ( "X509_STORE_new" );
        return 1; /* FAILED */
    }

    if ( section->ca_file )
    {
        if ( !SSL_CTX_load_verify_locations ( section->ctx,
                                              section->ca_file, NULL ) )
        {
            s_log ( LOG_ERR, "%s: Error loading verify certificates from %s",  __func__,
                    section->ca_file );
            sslerror ( "SSL_CTX_load_verify_locations" );
            return 1; /* FAILED */
        }

        /* revocation store needs CA certificates for CRL validation */
        if ( load_file_lookup ( section->revocation_store, section->ca_file ) )
            return 1; /* FAILED */

        /* trusted CA names sent to clients for client cert selection */
        if ( !section->option.client ) /* only performed on server */
        {
            s_log ( LOG_DEBUG, "%s: Client CA list: %s",  __func__,
                    section->ca_file );
            ca_dn = SSL_load_client_CA_file ( section->ca_file );

            for ( i = 0; i < sk_X509_NAME_num ( ca_dn ); ++i )
            {
                ca_name = X509_NAME2text ( sk_X509_NAME_value ( ca_dn, i ) );
                s_log ( LOG_INFO, "%s: Client CA: %s",  __func__, ca_name );
                str_free ( ca_name );
            }

            SSL_CTX_set_client_CA_list ( section->ctx, ca_dn );
        }
    }

    if ( section->ca_dir )
    {
        if ( !SSL_CTX_load_verify_locations ( section->ctx,
                                              NULL, section->ca_dir ) )
        {
            s_log ( LOG_ERR, "%s: Error setting verify directory to %s",  __func__,
                    section->ca_dir );
            sslerror ( "SSL_CTX_load_verify_locations" );
            return 1; /* FAILED */
        }

        s_log ( LOG_DEBUG, "%s: Verify directory set to %s",  __func__, section->ca_dir );
        add_dir_lookup ( section->revocation_store, section->ca_dir );
    }

    if ( section->crl_file )
        if ( load_file_lookup ( section->revocation_store, section->crl_file ) )
            return 1; /* FAILED */

    if ( section->crl_dir )
    {
        section->revocation_store->cache = 0; /* don't cache CRLs */
        add_dir_lookup ( section->revocation_store, section->crl_dir );
    }

    SSL_CTX_set_verify ( section->ctx, SSL_VERIFY_PEER |
                         ( section->verify_level >= 2 ? SSL_VERIFY_FAIL_IF_NO_PEER_CERT : 0 ),
                         verify_callback );

    if ( section->ca_dir && section->verify_level >= 3 )
        s_log ( LOG_INFO, "%s: Peer certificate location %s",  __func__, section->ca_dir );

    return 0; /* OK */
}

NOEXPORT int load_file_lookup ( X509_STORE *store, char *name )
{
    X509_LOOKUP *lookup;

    lookup = X509_STORE_add_lookup ( store, X509_LOOKUP_file() );

    if ( !lookup )
    {
        sslerror ( "X509_STORE_add_lookup" );
        return 1; /* FAILED */
    }

    if ( !X509_LOOKUP_load_file ( lookup, name, X509_FILETYPE_PEM ) )
    {
        s_log ( LOG_ERR, "%s: Failed to load %s revocation lookup file",  __func__, name );
        sslerror ( "X509_LOOKUP_load_file" );
        return 1; /* FAILED */
    }

    s_log ( LOG_DEBUG, "%s: Loaded %s revocation lookup file",  __func__, name );
    return 0; /* OK */
}

NOEXPORT int add_dir_lookup ( X509_STORE *store, char *name )
{
    X509_LOOKUP *lookup;

    lookup = X509_STORE_add_lookup ( store, X509_LOOKUP_hash_dir() );

    if ( !lookup )
    {
        sslerror ( "X509_STORE_add_lookup" );
        return 1; /* FAILED */
    }

    if ( !X509_LOOKUP_add_dir ( lookup, name, X509_FILETYPE_PEM ) )
    {
        s_log ( LOG_ERR, "%s: Failed to add %s revocation lookup directory",  __func__, name );
        sslerror ( "X509_LOOKUP_add_dir" );
        return 1; /* FAILED */
    }

    s_log ( LOG_DEBUG, "%s: Added %s revocation lookup directory",  __func__, name );
    return 0; /* OK */
}

/**************************************** verify callback */

NOEXPORT int verify_callback ( int preverify_ok, X509_STORE_CTX *callback_ctx )
{
    /* our verify callback function */
    SSL *ssl;
    CLI *c;

    /* retrieve application specific data */
    ssl = X509_STORE_CTX_get_ex_data ( callback_ctx,
                                       SSL_get_ex_data_X509_STORE_CTX_idx() );
    c = SSL_get_ex_data ( ssl, cli_index );

    if ( c->opt->verify_level < 1 )
    {
        s_log ( LOG_INFO, "%s: Certificate verification disabled",  __func__ );
        return 1; /* accept */
    }

    if ( verify_checks ( preverify_ok, callback_ctx ) )
        return 1; /* accept */

    if ( c->opt->option.client || c->opt->protocol )
        return 0; /* reject */

    if ( c->opt->redirect_addr.num ) /* pre-resolved addresses */
    {
        addrlist_dup ( &c->connect_addr, &c->opt->redirect_addr );
        s_log ( LOG_INFO, "%s: Redirecting connection",  __func__ );
        return 1; /* accept */
    }

    /* delayed lookup */
    if ( namelist2addrlist ( &c->connect_addr,
                             c->opt->redirect_list, DEFAULT_LOOPBACK ) )
    {
        s_log ( LOG_INFO, "%s: Redirecting connection",  __func__ );
        return 1; /* accept */
    }

    return 0; /* reject */
}

NOEXPORT int verify_checks ( int preverify_ok, X509_STORE_CTX *callback_ctx )
{
    X509 *cert;
    int depth;
    char *subject;

    cert = X509_STORE_CTX_get_current_cert ( callback_ctx );
    depth = X509_STORE_CTX_get_error_depth ( callback_ctx );
    subject = X509_NAME2text ( X509_get_subject_name ( cert ) );

    s_log ( LOG_DEBUG, "%s: Verification started at depth=%d: %s",  __func__, depth, subject );

    if ( !cert_check ( callback_ctx, preverify_ok ) )
    {
        s_log ( LOG_WARNING, "%s: Rejected by CERT at depth=%d: %s",  __func__, depth, subject );
        str_free ( subject );
        return 0; /* reject */
    }

    if ( !crl_check ( callback_ctx ) )
    {
        s_log ( LOG_WARNING, "%s: Rejected by CRL at depth=%d: %s",  __func__, depth, subject );
        str_free ( subject );
        return 0; /* reject */
    }

#ifndef OPENSSL_NO_OCSP

    if ( !ocsp_check ( callback_ctx ) )
    {
        s_log ( LOG_WARNING, "%s: Rejected by OCSP at depth=%d: %s",  __func__, depth, subject );
        str_free ( subject );
        return 0; /* reject */
    }

#endif /* !defined(OPENSSL_NO_OCSP) */

	s_log ( depth ? LOG_INFO : LOG_NOTICE,
	        "%s Certificate accepted at depth=%d: %s",  __func__, depth, subject );
	str_free ( subject );
	return 1; /* accept */
}

/**************************************** certificate checking */

NOEXPORT int cert_check ( X509_STORE_CTX *callback_ctx, int preverify_ok )
{
    SSL *ssl = X509_STORE_CTX_get_ex_data ( callback_ctx,
                                            SSL_get_ex_data_X509_STORE_CTX_idx() );
    CLI *c = SSL_get_ex_data ( ssl, cli_index );
    int depth = X509_STORE_CTX_get_error_depth ( callback_ctx );

    if ( preverify_ok )
    {
        s_log ( LOG_DEBUG, "%s: CERT: preverify ok",  __func__ );
    }
    else
    {
        /* remote site sent an invalid certificate */
        if ( c->opt->verify_level >= 4 && depth > 0 )
        {
            s_log ( LOG_INFO, "%s: CERT: Invalid CA certificate ignored",  __func__ );
            return 1; /* accept */
        }
        else
        {
            s_log ( LOG_WARNING, "%s: CERT: Verification error: %s",  __func__,
                    X509_verify_cert_error_string (
                        X509_STORE_CTX_get_error ( callback_ctx ) ) );
            /* retain the STORE_CTX error produced by pre-verification */
            return 0; /* reject */
        }
    }

    if ( c->opt->verify_level >= 3 && depth == 0 )
        if ( !cert_check_local ( callback_ctx ) )
            return 0; /* reject */

    return 1; /* accept */
}

NOEXPORT int cert_check_local ( X509_STORE_CTX *callback_ctx )
{
    X509 *cert = X509_STORE_CTX_get_current_cert ( callback_ctx );
    X509_OBJECT obj;
#if OPENSSL_VERSION_NUMBER>=0x10000000L
    STACK_OF ( X509 ) *sk;
    int i;

    sk = X509_STORE_get1_certs ( callback_ctx, X509_get_subject_name ( cert ) );

    if ( sk )
    {
        for ( i = 0; i < sk_X509_num ( sk ); i++ )
            if ( compare_pubkeys ( cert, sk_X509_value ( sk, i ) ) )
            {
                sk_X509_pop_free ( sk, X509_free );
                return 1; /* accept */
            }

        sk_X509_pop_free ( sk, X509_free );
    }

#endif

    /* pre-1.0.0 API only returns a single matching certificate */
    if ( X509_STORE_get_by_subject ( callback_ctx, X509_LU_X509,
                                     X509_get_subject_name ( cert ), &obj ) == 1 &&
            compare_pubkeys ( cert, obj.data.x509 ) )
        return 1; /* accept */

    s_log ( LOG_WARNING,
            "CERT: Certificate not found in local repository",  __func__ );
    X509_STORE_CTX_set_error ( callback_ctx, X509_V_ERR_CERT_REJECTED );
    return 0; /* reject */
}

NOEXPORT int compare_pubkeys ( X509 *c1, X509 *c2 )
{
#if OPENSSL_VERSION_NUMBER>=0x0090700fL
    ASN1_BIT_STRING *k1 = X509_get0_pubkey_bitstr ( c1 );
    ASN1_BIT_STRING *k2 = X509_get0_pubkey_bitstr ( c2 );

    if ( !k1 || !k2 || k1->length != k2->length || k1->length < 0 ||
            safe_memcmp ( k1->data, k2->data, ( size_t ) k1->length ) )
    {
        s_log ( LOG_DEBUG, "%s: CERT: Public keys do not match",  __func__ );
        return 0; /* reject */
    }

#else
    ( void ) c1; /* skip warning about unused parameter */
    ( void ) c2; /* skip warning about unused parameter */
#endif
    s_log ( LOG_INFO, "%s: CERT: Locally installed certificate matched",  __func__ );
    return 1; /* accept */
}

/**************************************** CRL checking */

/* based on the BSD-style licensed code of mod_ssl */
NOEXPORT int crl_check ( X509_STORE_CTX *callback_ctx )
{
    SSL *ssl;
    CLI *c;
    X509_STORE_CTX store_ctx;
    X509_OBJECT obj;
    X509_NAME *subject;
    X509_NAME *issuer;
    X509 *cert;
    X509_CRL *crl;
    X509_REVOKED *revoked;
    EVP_PKEY *pubkey;
    long serial;
    int i, n, rc;
    char *cp;
    ASN1_TIME *last_update = NULL, *next_update = NULL;

    ssl = X509_STORE_CTX_get_ex_data ( callback_ctx,
                                       SSL_get_ex_data_X509_STORE_CTX_idx() );
    c = SSL_get_ex_data ( ssl, cli_index );
    cert = X509_STORE_CTX_get_current_cert ( callback_ctx );
    subject = X509_get_subject_name ( cert );
    issuer = X509_get_issuer_name ( cert );

    /* try to retrieve a CRL corresponding to the _subject_ of
     * the current certificate in order to verify it's integrity */
    memset ( ( char * ) &obj, 0, sizeof obj );
    X509_STORE_CTX_init ( &store_ctx, c->opt->revocation_store, NULL, NULL );
    rc = X509_STORE_get_by_subject ( &store_ctx, X509_LU_CRL, subject, &obj );
    X509_STORE_CTX_cleanup ( &store_ctx );
    crl = obj.data.crl;

    if ( rc > 0 && crl )
    {
        cp = X509_NAME2text ( subject );
        s_log ( LOG_INFO, "%s: CRL: issuer: %s",  __func__, cp );
        str_free ( cp );
        last_update = X509_CRL_get_lastUpdate ( crl );
        next_update = X509_CRL_get_nextUpdate ( crl );
        log_time ( LOG_INFO, "CRL: last update", last_update );
        log_time ( LOG_INFO, "CRL: next update", next_update );

        /* verify the signature on this CRL */
        pubkey = X509_get_pubkey ( cert );

        if ( X509_CRL_verify ( crl, pubkey ) <= 0 )
        {
            s_log ( LOG_WARNING, "%s: CRL: Invalid signature",  __func__ );
            X509_STORE_CTX_set_error ( callback_ctx,
                                       X509_V_ERR_CRL_SIGNATURE_FAILURE );
            X509_OBJECT_free_contents ( &obj );

            if ( pubkey )
                EVP_PKEY_free ( pubkey );

            return 0; /* reject */
        }

        if ( pubkey )
            EVP_PKEY_free ( pubkey );

        /* check date of CRL to make sure it's not expired */
        if ( !next_update )
        {
            s_log ( LOG_WARNING, "%s: CRL: Invalid nextUpdate field",  __func__ );
            X509_STORE_CTX_set_error ( callback_ctx,
                                       X509_V_ERR_ERROR_IN_CRL_NEXT_UPDATE_FIELD );
            X509_OBJECT_free_contents ( &obj );
            return 0; /* reject */
        }

        if ( X509_cmp_current_time ( next_update ) < 0 )
        {
            s_log ( LOG_WARNING, "%s: CRL: CRL Expired - revoking all certificates",  __func__ );
            X509_STORE_CTX_set_error ( callback_ctx, X509_V_ERR_CRL_HAS_EXPIRED );
            X509_OBJECT_free_contents ( &obj );
            return 0; /* reject */
        }

        X509_OBJECT_free_contents ( &obj );
    }

    /* try to retrieve a CRL corresponding to the _issuer_ of
     * the current certificate in order to check for revocation */
    memset ( ( char * ) &obj, 0, sizeof obj );
    X509_STORE_CTX_init ( &store_ctx, c->opt->revocation_store, NULL, NULL );
    rc = X509_STORE_get_by_subject ( &store_ctx, X509_LU_CRL, issuer, &obj );
    X509_STORE_CTX_cleanup ( &store_ctx );
    crl = obj.data.crl;

    if ( rc > 0 && crl )
    {
        /* check if the current certificate is revoked by this CRL */
        n = sk_X509_REVOKED_num ( X509_CRL_get_REVOKED ( crl ) );

        for ( i = 0; i < n; i++ )
        {
            revoked = sk_X509_REVOKED_value ( X509_CRL_get_REVOKED ( crl ), i );

            if ( ASN1_INTEGER_cmp ( revoked->serialNumber,
                                    X509_get_serialNumber ( cert ) ) == 0 )
            {
                serial = ASN1_INTEGER_get ( revoked->serialNumber );
                cp = X509_NAME2text ( issuer );
                s_log ( LOG_WARNING, "%s: CRL: Certificate with serial %ld (0x%lX) "
                        "revoked per CRL from issuer %s",  __func__, serial, serial, cp );
                str_free ( cp );
                X509_STORE_CTX_set_error ( callback_ctx, X509_V_ERR_CERT_REVOKED );
                X509_OBJECT_free_contents ( &obj );
                return 0; /* reject */
            }
        }

        X509_OBJECT_free_contents ( &obj );
    }

    return 1; /* accept */
}

#ifndef OPENSSL_NO_OCSP

/**************************************** OCSP checking */

/* type checks not available -- use generic functions */
#ifndef sk_OPENSSL_STRING_num
#define sk_OPENSSL_STRING_num(st) sk_num(st)
#endif
#ifndef sk_OPENSSL_STRING_value
#define sk_OPENSSL_STRING_value(st, i) sk_value((st),(i))
#endif

NOEXPORT int ocsp_check ( X509_STORE_CTX *callback_ctx )
{
    SSL *ssl;
    CLI *c;
    X509 *cert;
    OCSP_CERTID *cert_id;
    STACK_OF ( OPENSSL_STRING ) *aia;
    int i, status = V_OCSP_CERTSTATUS_UNKNOWN;

    /* get the current certificate ID */
    cert = X509_STORE_CTX_get_current_cert ( callback_ctx );

    if ( !cert )
    {
        s_log ( LOG_ERR, "%s: OCSP: Failed to get the current certificate",  __func__ );
        X509_STORE_CTX_set_error ( callback_ctx,
                                   X509_V_ERR_APPLICATION_VERIFICATION );
        return 0; /* reject */
    }

    if ( !X509_NAME_cmp ( X509_get_subject_name ( cert ),
                          X509_get_issuer_name ( cert ) ) )
    {
        s_log ( LOG_DEBUG, "%s: OCSP: Ignoring root certificate",  __func__ );
        return 1; /* accept */
    }

    cert_id = OCSP_cert_to_id ( NULL, cert, get_current_issuer ( callback_ctx ) );

    if ( !cert_id )
    {
        sslerror ( "OCSP: OCSP_cert_to_id" );
        X509_STORE_CTX_set_error ( callback_ctx,
                                   X509_V_ERR_APPLICATION_VERIFICATION );
        return 0; /* reject */
    }

    ssl = X509_STORE_CTX_get_ex_data ( callback_ctx,
                                       SSL_get_ex_data_X509_STORE_CTX_idx() );
    c = SSL_get_ex_data ( ssl, cli_index );

    /* use the responder specified in the configuration file */
    if ( c->opt->ocsp_url )
    {
        s_log ( LOG_DEBUG, "%s: OCSP: Connecting configured responder \"%s\"",  __func__,
                c->opt->ocsp_url );

        if ( ocsp_request ( c, callback_ctx, cert_id, c->opt->ocsp_url ) !=
                V_OCSP_CERTSTATUS_GOOD )
            return 0; /* reject */
    }

    /* use the responder from AIA (Authority Information Access) */
    if ( !c->opt->option.aia )
        return 1; /* accept */

    aia = X509_get1_ocsp ( cert );

    if ( !aia )
        return 1; /* accept */

    for ( i = 0; i < sk_OPENSSL_STRING_num ( aia ); i++ )
    {
        s_log ( LOG_DEBUG, "%s: OCSP: Connecting AIA responder \"%s\"",  __func__,
                sk_OPENSSL_STRING_value ( aia, i ) );
        status = ocsp_request ( c, callback_ctx, cert_id,
                                sk_OPENSSL_STRING_value ( aia, i ) );

        if ( status != V_OCSP_CERTSTATUS_UNKNOWN )
            break; /* we received a definitive response */
    }

    X509_email_free ( aia );

    if ( status == V_OCSP_CERTSTATUS_GOOD )
        return 1; /* accept */

    return 0; /* reject */
}

/* returns one of:
 * V_OCSP_CERTSTATUS_GOOD
 * V_OCSP_CERTSTATUS_REVOKED
 * V_OCSP_CERTSTATUS_UNKNOWN */
NOEXPORT int ocsp_request ( CLI *c, X509_STORE_CTX *callback_ctx,
                            OCSP_CERTID *cert_id, char *url )
{
    int status = V_OCSP_CERTSTATUS_UNKNOWN;
    int reason;
    int ctx_err = X509_V_ERR_APPLICATION_VERIFICATION;
    OCSP_REQUEST *request = NULL;
    OCSP_RESPONSE *response = NULL;
    OCSP_BASICRESP *basic_response = NULL;
    ASN1_GENERALIZEDTIME *revoked_at = NULL,
                          *this_update = NULL, *next_update = NULL;

    /* build request */
    request = OCSP_REQUEST_new();

    if ( !request )
    {
        sslerror ( "OCSP: OCSP_REQUEST_new" );
        goto cleanup;
    }

    if ( !OCSP_request_add0_id ( request, cert_id ) )
    {
        sslerror ( "OCSP: OCSP_request_add0_id" );
        goto cleanup;
    }

    OCSP_request_add1_nonce ( request, NULL, -1 );

    /* send the request and get a response */
    response = ocsp_get_response ( c, request, url );

    if ( !response )
        goto cleanup;

    status = OCSP_response_status ( response );

    if ( status != OCSP_RESPONSE_STATUS_SUCCESSFUL )
    {
        s_log ( LOG_WARNING, "%s: OCSP: Responder error: %d: %s",  __func__,
                status, OCSP_response_status_str ( status ) );
        goto cleanup;
    }

    s_log ( LOG_DEBUG, "%s: OCSP: Response received",  __func__ );

    /* verify the response */
    basic_response = OCSP_response_get1_basic ( response );

    if ( !basic_response )
    {
        sslerror ( "OCSP: OCSP_response_get1_basic" );
        goto cleanup;
    }

    if ( OCSP_check_nonce ( request, basic_response ) <= 0 )
    {
        s_log ( LOG_WARNING, "%s: OCSP: Invalid nonce",  __func__ );
        goto cleanup;
    }

    if ( OCSP_basic_verify ( basic_response, NULL,
                             c->opt->revocation_store, c->opt->ocsp_flags ) <= 0 )
    {
        sslerror ( "OCSP: OCSP_basic_verify" );
        goto cleanup;
    }

    if ( !OCSP_resp_find_status ( basic_response, cert_id, &status, &reason,
                                  &revoked_at, &this_update, &next_update ) )
    {
        sslerror ( "OCSP: OCSP_resp_find_status" );
        goto cleanup;
    }

    s_log ( LOG_NOTICE, "%s: OCSP: Status: %s",  __func__, OCSP_cert_status_str ( status ) );
    log_time ( LOG_INFO, "OCSP: This update", this_update );
    log_time ( LOG_INFO, "OCSP: Next update", next_update );

    /* check if the response is valid for at least one minute */
    if ( !OCSP_check_validity ( this_update, next_update, 60, -1 ) )
    {
        sslerror ( "OCSP: OCSP_check_validity" );
        status = V_OCSP_CERTSTATUS_UNKNOWN;
        goto cleanup;
    }

    switch ( status )
    {
    case V_OCSP_CERTSTATUS_GOOD:
        break;
    case V_OCSP_CERTSTATUS_REVOKED:

        if ( reason == -1 )
            s_log ( LOG_WARNING, "%s: OCSP: Certificate revoked",  __func__ );
        else
            s_log ( LOG_WARNING, "%s: OCSP: Certificate revoked: %d: %s",  __func__,
                    reason, OCSP_crl_reason_str ( reason ) );

        log_time ( LOG_NOTICE, "OCSP: Revoked at", revoked_at );
        ctx_err = X509_V_ERR_CERT_REVOKED;
        break;
    case V_OCSP_CERTSTATUS_UNKNOWN:
        s_log ( LOG_WARNING, "%s: OCSP: Unknown verification status",  __func__ );
    }

cleanup:

    if ( request )
        OCSP_REQUEST_free ( request );

    if ( response )
        OCSP_RESPONSE_free ( response );

    if ( basic_response )
        OCSP_BASICRESP_free ( basic_response );

    if ( status != V_OCSP_CERTSTATUS_GOOD )
        X509_STORE_CTX_set_error ( callback_ctx, ctx_err );

    return status;
}

NOEXPORT OCSP_RESPONSE *ocsp_get_response ( CLI *c,
        OCSP_REQUEST *req, char *url )
{
    BIO *bio = NULL;
    OCSP_REQ_CTX *req_ctx = NULL;
    OCSP_RESPONSE *resp = NULL;
    int err;
    char *host = NULL, *port = NULL, *path = NULL;
    SOCKADDR_UNION addr;
    int ssl;

    /* parse the OCSP URL */
    if ( !OCSP_parse_url ( url, &host, &port, &path, &ssl ) )
    {
        s_log ( LOG_ERR, "%s: OCSP: Failed to parse the OCSP URL",  __func__ );
        goto cleanup;
    }

    if ( ssl )
    {
        s_log ( LOG_ERR, "%s: OCSP: SSL not supported for OCSP"
                " - additional stunnel service needs to be defined",  __func__ );
        goto cleanup;
    }

    memset ( &addr, 0, sizeof addr );
    addr.in.sin_family = AF_INET;

    if ( !hostport2addr ( &addr, host, port ) )
    {
        s_log ( LOG_ERR, "%s: OCSP: Failed to resolve the OCSP server address",  __func__ );
        goto cleanup;
    }

    /* connect specified OCSP server (responder) */
    c->fd = s_socket ( addr.sa.sa_family, SOCK_STREAM, 0, 1, "OCSP: socket" );

    if ( c->fd < 0 )
        goto cleanup;

    if ( s_connect ( c, &addr, addr_len ( &addr ) ) )
        goto cleanup;

    bio = BIO_new_fd ( c->fd, BIO_NOCLOSE );

    if ( !bio )
        goto cleanup;

    s_log ( LOG_DEBUG, "%s: OCSP: response retrieved",  __func__ );

    /* OCSP protocol communication loop */
    req_ctx = OCSP_sendreq_new ( bio, path, req, -1 );

    if ( !req_ctx )
    {
        sslerror ( "OCSP: OCSP_sendreq_new" );
        goto cleanup;
    }

    while ( OCSP_sendreq_nbio ( &resp, req_ctx ) == -1 )
    {
        s_poll_init ( c->fds );
        s_poll_add ( c->fds, c->fd, BIO_should_read ( bio ), BIO_should_write ( bio ) );
        err = s_poll_wait ( c->fds, c->opt->timeout_busy, 0 );

        if ( err == -1 )
            sockerror ( "OCSP: s_poll_wait" );

        if ( err == 0 )
            s_log ( LOG_INFO, "%s: OCSP: s_poll_wait: TIMEOUTbusy exceeded",  __func__ );

        if ( err <= 0 )
            goto cleanup;
    }

#if 0
    s_log ( LOG_DEBUG, "%s: OCSP: context state: 0x%x", * ( int * ) req_ctx );
#endif

    /* http://www.mail-archive.com/openssl-users@openssl.org/msg61691.html */
    if ( resp )
    {
        s_log ( LOG_DEBUG, "%s: OCSP: request completed",  __func__ );
    }
    else
    {
        if ( ERR_peek_error() )
            sslerror ( "OCSP: OCSP_sendreq_nbio" );
        else /* OpenSSL error: OCSP_sendreq_nbio does not use OCSPerr */
            s_log ( LOG_ERR, "%s: OCSP: OCSP_sendreq_nbio: OpenSSL internal error",  __func__ );
    }

cleanup:

    if ( req_ctx )
        OCSP_REQ_CTX_free ( req_ctx );

    if ( bio )
        BIO_free_all ( bio );

    if ( c->fd >= 0 )
    {
        closesocket ( c->fd );
        c->fd = -1; /* avoid double close on cleanup */
    }

    if ( host )
        OPENSSL_free ( host );

    if ( port )
        OPENSSL_free ( port );

    if ( path )
        OPENSSL_free ( path );

    return resp;
}

#endif /* !defined(OPENSSL_NO_OCSP) */

/* find the issuer certificate without lookups */
NOEXPORT X509 *get_current_issuer ( X509_STORE_CTX *callback_ctx )
{
    STACK_OF ( X509 ) *chain;
    int depth;

    chain = X509_STORE_CTX_get_chain ( callback_ctx );
    depth = X509_STORE_CTX_get_error_depth ( callback_ctx );

    if ( depth < sk_X509_num ( chain ) - 1 ) /* not the root CA cert */
        ++depth; /* index of the issuer cert */

    return sk_X509_value ( chain, depth );
}

char *X509_NAME2text ( X509_NAME *name )
{
    char *text;
    BIO *bio;
    int n;

    bio = BIO_new ( BIO_s_mem() );

    if ( !bio )
        return str_dup ( "BIO_new() failed" );

    X509_NAME_print_ex ( bio, name, 0,
                         XN_FLAG_ONELINE & ~ASN1_STRFLGS_ESC_MSB & ~XN_FLAG_SPC_EQ );
    n = BIO_pending ( bio );
    text = str_alloc ( ( size_t ) n + 1 );
    n = BIO_read ( bio, text, n );

    if ( n < 0 )
    {
        BIO_free ( bio );
        str_free ( text );
        return str_dup ( "BIO_read() failed" );
    }

    text[n] = '\0';
    BIO_free ( bio );
    return text;
}

NOEXPORT void log_time ( const int level, const char *txt, ASN1_TIME *t )
{
    char *cp;
    BIO *bio;
    int n;

    if ( !t )
        return;

    bio = BIO_new ( BIO_s_mem() );

    if ( !bio )
        return;

    ASN1_TIME_print ( bio, t );
    n = BIO_pending ( bio );
    cp = str_alloc ( ( size_t ) n + 1 );
    n = BIO_read ( bio, cp, n );

    if ( n < 0 )
    {
        BIO_free ( bio );
        str_free ( cp );
        return;
    }

    cp[n] = '\0';
    BIO_free ( bio );
    s_log ( level, "%s: %s", txt, cp );
    str_free ( cp );
}

/* end of verify.c */
