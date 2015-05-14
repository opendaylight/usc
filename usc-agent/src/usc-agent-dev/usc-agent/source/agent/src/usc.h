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
#include <stdio.h>
#include <sys/time.h>
#include <signal.h>


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

#define TLS_NETCONF_PORT  6513


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

/* USC Socket Errors while connecting to Server */
typedef enum {
    USC_ERR_EPERM = 1,          /* Operation not permitted */
    USC_ERR_ENOENT,             /* 2   No such file or directory */
    USC_ERR_ESRCH,              /* 3 No such process */
    USC_ERR_EINTR,              /* 4   Interrupted system call */
    USC_ERR_EIO,                /* 5   I/O error */
    USC_ERR_ENXIO,              /* 6   No such device or address */
    USC_ERR_E2BIG,              /* 7  Argument list too long */
    USC_ERR_ENOEXEC,            /* 8   Exec format error */
    USC_ERR_EBADF,              /* 9   Bad file number */
    USC_ERR_ECHILD,             /* 10  No child processes */
    USC_ERR_EAGAIN,             /* 11  Try again */
    USC_ERR_ENOMEM,             /* 12 Out of memory */
    USC_ERR_EACCES,             /* 13  Permission denied */
    USC_ERR_EFAULT,             /* 14  Bad address */
    USC_ERR_ENOTBLK,            /* 15  Block device required */
    USC_ERR_EBUSY,              /* 16  Device or resource busy */
    USC_ERR_EEXIST,             /* 17  File exists */
    USC_ERR_EXDEV,              /* 18  Cross-device link */
    USC_ERR_ENODEV,             /* 19  No such device */
    USC_ERR_ENOTDIR,            /* 20  Not a directory */
    USC_ERR_EISDIR,             /* 21  Is a directory */
    USC_ERR_EINVAL,             /* 22   Invalid argument */
    USC_ERR_ENFILE,             /* 23   File table overflow */
    USC_ERR_EMFILE,             /* 24   Too many open files */
    USC_ERR_ENOTTY,             /* 25   Not a typewriter */
    USC_ERR_ETXTBSY,            /* 26   Text file busy */
    USC_ERR_EFBIG,              /* 27   File too large */
    USC_ERR_ENOSPC,             /* 28   No space left on device */
    USC_ERR_ESPIPE,             /* 29   Illegal seek */
    USC_ERR_EROFS,              /* 30   Read-only file system */
    USC_ERR_EMLINK,             /* 31   Too many links */
    USC_ERR_EPIPE,              /* 32   Broken pipe */
    USC_ERR_EDOM,               /* 33   Math argument out of domain of func */
    USC_ERR_ERANGE,             /* 34   Math result not representable */
    USC_ERR_EDEADLK,            /* 35   Resource deadlock would occur */
    USC_ERR_ENAMETOOLONG,       /* 36   File name too long */
    USC_ERR_ENOLCK,             /* 37   No record locks available */
    USC_ERR_ENOSYS,             /* 38   Function not implemented */
    USC_ERR_ENOTEMPTY,          /* 39   Directory not empty */
    USC_ERR_ELOOP,              /* 40   Too many symbolic links encountered */
    USC_ERR_EWOULDBLOCK,        /*  EAGAIN   Operation would block */
    USC_ERR_ENOMSG,             /*  42   No message of desired type */
    USC_ERR_EIDRM,              /*  43   Identifier removed */
    USC_ERR_ECHRNG,             /*  44   Channel number out of range */
    USC_ERR_EL2NSYNC,           /*  45   Level 2 not synchronized */
    USC_ERR_EL3HLT,             /*  46   Level 3 halted */
    USC_ERR_EL3RST,             /*  47   Level 3 reset */
    USC_ERR_ELNRNG,             /*  48   Link number out of range */
    USC_ERR_EUNATCH,            /*  49   Protocol driver not attached */
    USC_ERR_ENOCSI,             /*  50   No CSI structure available */
    USC_ERR_EL2HLT,             /*  51   Level 2 halted */
    USC_ERR_EBADE,              /*  52   Invalid exchange */
    USC_ERR_EBADR,              /*  53   Invalid request descriptor */
    USC_ERR_EXFULL,             /*  54   Exchange full */
    USC_ERR_ENOANO,             /*  55   No anode */
    USC_ERR_EBADRQC,            /*  56   Invalid request code */
    USC_ERR_EBADSLT,            /*  57   Invalid slot */    
    USC_ERR_EDEADLOCK,          /*  EDEADLK */
    USC_ERR_EBFONT,             /*  59   Bad font file format */
    USC_ERR_ENOSTR,             /*  60   Device not a stream */
    USC_ERR_ENODATA,            /*  61   No data available */
    USC_ERR_ETIME,              /*  62   Timer expired */
    USC_ERR_ENOSR,              /*  63   Out of streams resources */
    USC_ERR_ENONET,             /*  64   Machine is not on the network */
    USC_ERR_ENOPKG,             /*  65   Package not installed */
    USC_ERR_EREMOTE,            /*  66   Object is remote */
    USC_ERR_ENOLINK,            /*  67   Link has been severed */
    USC_ERR_EADV,               /*  68   Advertise error */
    USC_ERR_ESRMNT,             /*  69   Srmount error */
    USC_ERR_ECOMM,              /*  70   Communication error on send */
    USC_ERR_EPROTO,             /*  71   Protocol error */
    USC_ERR_EMULTIHOP,          /*  72   Multihop attempted */
    USC_ERR_EDOTDOT,            /*  73   RFS specific error */
    USC_ERR_EBADMSG,            /*  74   Not a data message */
    USC_ERR_EOVERFLOW,          /*  75   Value too large for defined data type */
    USC_ERR_ENOTUNIQ,           /*  76   Name not unique on network */
    USC_ERR_EBADFD,             /*  77   File descriptor in bad state */
    USC_ERR_EREMCHG,            /*  78   Remote address changed */
    USC_ERR_ELIBACC,            /*  79   Can not access a needed shared library */
    USC_ERR_ELIBBAD,            /*  80   Accessing a corrupted shared library */
    USC_ERR_ELIBSCN,            /*  81   .lib section in a.out corrupted */
    USC_ERR_ELIBMAX,            /*  82   Attempting to link in too many shared libraries */
    USC_ERR_ELIBEXEC,           /*  83   Cannot exec a shared library directly */
    USC_ERR_EILSEQ,             /*  84   Illegal byte sequence */
    USC_ERR_ERESTART,           /*  85   Interrupted system call should be restarted */
    USC_ERR_ESTRPIPE,           /*  86   Streams pipe error */
    USC_ERR_EUSERS,             /*  87   Too many users */
    USC_ERR_ENOTSOCK,           /*  88   Socket operation on non-socket */
    USC_ERR_EDESTADDRREQ,       /*  89   Destination address required */
    USC_ERR_EMSGSIZE,           /*  90   Message too long */
    USC_ERR_EPROTOTYPE,         /*  91   Protocol wrong type for socket */
    USC_ERR_ENOPROTOOPT,        /*  92   Protocol not available */
    USC_ERR_EPROTONOSUPPORT,    /*  93   Protocol not supported */
    USC_ERR_ESOCKTNOSUPPORT,    /*  94   Socket type not supported */
    USC_ERR_EOPNOTSUPP,         /*  95   Operation not supported on transport endpoint */
    USC_ERR_EPFNOSUPPORT,       /*  96   Protocol family not supported */
    USC_ERR_EAFNOSUPPORT,       /*  97   Address family not supported by protocol */
    USC_ERR_EADDRINUSE,         /*  98   Address already in use */
    USC_ERR_EADDRNOTAVAIL,      /*  99   Cannot assign requested address */
    USC_ERR_ENETDOWN,           /*  100  Network is down */
    USC_ERR_ENETUNREACH,        /*  101  Network is unreachable */
    USC_ERR_ENETRESET,          /*  102  Network dropped connection because of reset */
    USC_ERR_ECONNABORTED,       /*  103  Software caused connection abort */
    USC_ERR_ECONNRESET,         /*  104  Connection reset by peer */
    USC_ERR_ENOBUFS,            /*  105  No buffer space available */
    USC_ERR_EISCONN,            /*  106  Transport endpoint is already connected */
    USC_ERR_ENOTCONN,           /*  107  Transport endpoint is not connected */
    USC_ERR_ESHUTDOWN,          /*  108  Cannot send after transport endpoint shutdown */
    USC_ERR_ETOOMANYREFS,       /*  109  Too many references: cannot splice */
    USC_ERR_ETIMEDOUT,          /*  110  Connection timed out */
    USC_ERR_ECONNREFUSED,       /*  111  Connection refused */
    USC_ERR_EHOSTDOWN,          /*  112  Host is down */
    USC_ERR_EHOSTUNREACH,       /*  113  No route to host */
    USC_ERR_EALREADY,           /*  114  Operation already in progress */
    USC_ERR_EINPROGRESS,        /*  115  Operation now in progress */
    USC_ERR_ESTALE,             /*  116  Stale NFS file handle */
    USC_ERR_EUCLEAN,            /*  117  Structure needs cleaning */
    USC_ERR_ENOTNAM,            /*  118  Not a XENIX named type file */
    USC_ERR_ENAVAIL,            /*  119  No XENIX semaphores available */
    USC_ERR_EISNAM,             /*  120  Is a named type file */
    USC_ERR_EREMOTEIO,          /*  121  Remote I/O error */
    USC_ERR_EDQUOT,             /*  122  Quota exceeded */
    USC_ERR_ENOMEDIUM,          /*  123  No medium found */
    USC_ERR_EMEDIUMTYPE,        /*  124  Wrong medium type */
    USC_ERR_ECANCELED,          /*  125  Operation Canceled */
    USC_ERR_ENOKEY,             /*  126  Required key not available */
    USC_ERR_EKEYEXPIRED,        /*  127  Key has expired */
    USC_ERR_EKEYREVOKED,        /*  128   Key has been revoked */
    USC_ERR_EKEYREJECTED,       /*  129 Key was rejected by service */
    USC_ERR_MAX
}usc_socket_err_t;

/******* usc header *********/
typedef struct __attribute__((__packed__))
{
    uint8_t usc_version:4;
    uint8_t usc_op:4;
    uint8_t sec_trans;
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
    uint8_t *msg, 
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
    void *, 
    uint16_t len,
    usc_operation_type_t op,
    usc_sec_transport_t sc
    );

/* heart beat response for the usc protocol */
extern uint32_t send_heartbeat_resp (
    usc_header *usc, 
    void * c, 
    uint16_t len,
    usc_operation_type_t op,
    usc_sec_transport_t sc
    ); 
