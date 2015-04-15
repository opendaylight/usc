/* This is USC protocol specification *****************************************
 *
 *
 *
 *  author - Ravi S Chuppala 
 *  Dt 03/19/2015
 ******************************************************************************/

/****** USC packet header format *********************************************

 ---------    -----------    --------   ------------   ---------    ----------
|USC Ver  |   |USC op type| | App ID | | App Session| |Sec Trans|  | Payload |
 ---------    -----------    --------   ------------   ---------    ---------- 
<-4 bits-->   <--4bits--->  <-2 bytes-> <--2 bytes--> <-1 byte-->  <-2 bytes->

******************************************************************************/
#include "common.h"


/* NETCONF protocol support */
/**
 * @brief NETCONF v1.0 message start (part of the chunked framing mechanism).
 * @
 * Hex values: 0x3c 0x3f 0x78 0x6d 0x6c
*/ 
#define NC_V10_START_MSG    "<?xml"

/**
 * @brief NETCONF v1.1 message start (part of the chunked framing mechanism).
 * @ingroup internalAPI
 * Hex values: 0x0A 0x23
 */
#define NC_V11_START_MSG    "\n#"

/**
 * @brief NETCONF v1.0 message end (part of the chunked framing mechanism).
 * @
 * hex values: 0x5d 0x5d 0x3e 0x5d 0x5d 0x3e
*/ 
#define NC_V10_END_MSG    "]]>]]>"

/**
 * @brief NETCONF v1.1 message end (part of the chunked framing mechanism).
 * @
 * hex values: 0x0A 0x23 0x23 0x0A
*/ 
#define NC_V11_END_MSG    "\n##\n"


/* USC intial verson ****************/
#define USC_VERSION    1


/***** USC operation types *******/
typedef enum {
    USC_OP_HELLO_REQ = 1,
    USC_OP_HELLO_RESP,
    USC_OP_ACKNOWLEDGE,
    USC_OP_DATA,
    USC_OP_SERVICE_UPDATE,
    USC_OP_SERVICE_ACK,
    USC_OP_HEALTH_STAT_REQ,
    USC_OP_HEALTH_STAT_RESP,
    USC_OP_ALERTS,
    USC_OP_HEART_BEAT_REQ,
    USC_OP_HEART_BEAT_RESP,
    USC_OP_MAX
} usc_operation_type_t;

/*** USC Security Transport type ****/
typedef enum {
    USC_SEC_TRANS_TLS = 1,
    USC_SEC_TRANS_DTLS, 
    USC_SEC_TRANS_SSH,
    USC_SEC_TRANS_IPSEC,
    USC_SEC_MAX
}usc_sec_transport_t;


/******* usc header *********/
typedef struct __attribute__((__packed__))
{
    uint8_t usc_version:4;
    uint8_t usc_op:4;
    uint8_t Security_transport;
    uint16_t app_id;
    uint16_t app_session;
    uint16_t payload_length;
}usc_header;

/* this function adds USC header */
extern uint32_t add_usc_header ( 
    usc_header *usc, 
    void *, 
    uint16_t len,
    usc_operation_type_t op,
    usc_sec_transport_t sc
    ); 

/* this funciton parse and prints the content of the protcol */
extern uint32_t parse_usc_header ( 
    uint8_t *msg, 
    void *c
    );

/* this funciton removes and adjust the offset of the pointer */
extern uint32_t remove_usc_header ( 
    uint8_t *msg 
    );

/* validate the usc headers */
extern uint32_t validate_usc_header ( 
    uint8_t *msg 
    );

/* in case of no server or server failures handle the cases */
extern uint32_t prepare_error_handling ( 
    uint8_t**msg, 
    uint16_t app_id, 
    uint16_t app_session, 
    int err
    );

/* mapping function for fd to 2 tupple for same protocol multiple sessions*/
extern uint32_t map_2_tupple_to_fd ( 
    uint8_t *msg,
    uint16_t app_id,
    uint16_t app_session
    );

/*  Debug functionality */
extern void hexDebugDump ( 
    uint8_t *data, 
    size_t size 
    );

/* heart beat request for the usc protocol */
extern uint32_t send_heartbeat_req (
    usc_header *usc, 
    void *c, 
    uint16_t len,
    usc_operation_type_t op,
    usc_sec_transport_t sc
    );

/* heart beat response for the usc protocol */
extern uint32_t send_heartbeat_resp (
    usc_header *usc, 
    void *c, 
    uint16_t len,
    usc_operation_type_t op,
    usc_sec_transport_t sc
    ); 

