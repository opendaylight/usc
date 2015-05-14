/* This is USC protocol specification *****************************************
 *
 *
 *
 *  author - Ravi S Chuppala 
 *  Dt 03/19/2015
 ******************************************************************************/


#include "common.h"
#include "prototypes.h"
extern int call_home_mode;


/* this function adds USC header */
uint32_t add_usc_header( usc_header *usc, void *c, uint16_t len, usc_operation_type_t op, usc_sec_transport_t sc) 
{

    usc_header *local_usc = usc;
    CLI *cli = (CLI *)c;
    local_usc->usc_op = op;
    local_usc->sec_trans  = sc;
    
    if( !cli->opt->option.client && !call_home_mode)
    {
        local_usc->usc_version = cli->usc.usc_version;
        local_usc->app_id = htons(cli->usc.app_id);
        local_usc->app_session = htons(cli->usc.app_session);
    } 
    else
    {
        local_usc->usc_version = USC_VERSION;
        local_usc->app_id = htons(TLS_NETCONF_PORT);
        local_usc->app_session = htons(1000);
    }      
    local_usc->payload_length      = htons(len);

    s_log(LOG_DEBUG, "ADD in  version:%d usc_op:%d sec :%d app_id :%d session :%d payload:%d  \n",
           local_usc->usc_version,
            local_usc->usc_op ,
           local_usc->sec_trans,
           ntohs (local_usc->app_id ),
           ntohs (local_usc->app_session),
           ntohs (local_usc->payload_length ));
    usc =  local_usc;      
    return 0;
}

/* this function parse and prints the content of the protcol */
uint32_t parse_usc_header( uint8_t *msg, void *c )
{
    CLI *cli = (CLI *)c; 
    usc_header *read_usc = (usc_header *)msg;
    s_log(LOG_DEBUG, "MSG version:%d usc_op:%d app_id:%d session:%d sec:%d payload:%d  \n",
               read_usc->usc_version,
               read_usc->usc_op,
               ntohs(read_usc->app_id),
               ntohs(read_usc->app_session),
               read_usc->sec_trans,
               ntohs(read_usc->payload_length));
    if((read_usc->usc_version != cli->usc.usc_version) ||
        (cli->usc.app_id != ntohs(read_usc->app_id)) ||
        (cli->usc.app_session != ntohs(read_usc->app_session)) ||
        (cli->usc.sec_trans != read_usc->sec_trans)) 
    {
        s_log(LOG_DEBUG, "CLI Another Session Exist version:%d usc_op:%d app_id:%d session:%d sec:%d payload:%d  \n",
                 cli->usc.usc_version,
                 cli->usc.usc_op,
                 cli->usc.app_id,
                 cli->usc.app_session,
                 cli->usc.sec_trans,
                 cli->usc.payload_length);            
                 return 1;
    }
    return 0;
}

/* this function removes and adjust the offset of the pointer */
uint8_t*  adjust_usc_header_payload(uint8_t **msg, int size)
{
    uint8_t *buf = *msg;
    memmove ( buf + sizeof(usc_header), buf, size);
    return buf;
}

/* this function validates the pointer */
uint32_t validate_usc_header (uint8_t *msg)
{
    usc_header *read_usc;
    read_usc = (usc_header *)msg;
    s_log(LOG_DEBUG, "version:%d usc_op:%d app_id:%d session:%d sec:%d payload:%d  \n",
        read_usc->usc_version,
        read_usc->usc_op,
        ntohs(read_usc->app_id),
        ntohs(read_usc->app_session),
        read_usc->sec_trans,
        ntohs(read_usc->payload_length ));

    return 0;
}

/* this function handles the error condition */
uint32_t prepare_error_handling(uint8_t*msg, uint16_t port, uint16_t app_session, int err)
{
    usc_header usc;  
    usc.usc_version         = USC_VERSION;
    usc.usc_op              = USC_OP_ALERTS;
    usc.app_id      = htons(port);
    usc.app_session = htons(app_session);
    usc.sec_trans  = USC_SEC_TRANS_TLS;
    usc.payload_length      = (uint16_t)(htons(err));
    memcpy(msg,(uint8_t *)&usc, sizeof(usc_header));     
    //clear_socket_error();
    return 0;
}

/* validate the usc headers */
uint32_t netconf_header_validate(uint8_t *msg)
{
    return 0;
}

/* mapping function for fd to 2 tupple */
uint32_t map_2_tupple_to_fd(uint8_t *msg,
                                    uint16_t app_id,
                                    uint16_t app_session)
{
    return 0;
}

/* heart beat request for the usc protocol */
uint32_t send_heartbeat_req(usc_header *usc, 
                                void *c, 
                                uint16_t len,
                                usc_operation_type_t op,
                                usc_sec_transport_t sc
                                )
{
    return 0;
}
                                
/* heart beat response for the usc protocol */
uint32_t send_heartbeat_resp(usc_header *usc, 
                                void *c, 
                                uint16_t len,
                                usc_operation_type_t op,
                                usc_sec_transport_t sc
                                )
{
    return 0;
}

