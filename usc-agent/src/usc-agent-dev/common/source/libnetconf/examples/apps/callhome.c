/*
 * reverse_server.c
 *
 *  Created on: 12. 3. 2014
 *      Author: krejci
 */

#include <unistd.h>
#include <stdio.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <string.h>
#include <errno.h>
#include <syslog.h>
#include <stdlib.h>

#include <sys/un.h>


#include "libnetconf.h"
#include "libnetconf_ssh.h"
#include "libnetconf_tls.h"

#define STUNNEL "/home/test/usc-agent-dev/usc-agent/source/agent/src/stunnel"

void clb_print(NC_VERB_LEVEL level, const char* msg)
{

	switch (level) {
	case NC_VERB_ERROR:
		syslog(LOG_ERR, "%s", msg);
		break;
	case NC_VERB_WARNING:
		syslog(LOG_WARNING, "%s", msg);
		break;
	case NC_VERB_VERBOSE:
		syslog(LOG_INFO, "%s", msg);
		break;
	case NC_VERB_DEBUG:
		syslog(LOG_DEBUG, "%s", msg);
		break;
	}
}

static void print_usage (char * progname)
{
	fprintf (stdout, "Usage: %s [-ht] host:port\n", progname);
	fprintf (stdout, " -h       display help\n");
	fprintf (stdout, " -t       Use TLS (SSH is used by default)\n");
	exit (0);
}
#define OPTSTRING "ht"

int main(int argc, char* argv[])
{
	struct nc_mngmt_server *srv;
	char *host, *port;
	int next_option;
	int retpid, pid, status;
	NC_TRANSPORT proto = NC_TRANSPORT_SSH;
	int  sock = -1;

	/* parse given options */
	while ((next_option = getopt (argc, argv, OPTSTRING)) != -1) {
		switch (next_option) {
		case 'h':
			print_usage(argv[0]);
			break;
		case 't':
			proto = NC_TRANSPORT_TLS;
			break;
		default:
			print_usage(argv[0]);
			break;
		}
	}
	if ((optind + 1) > argc) {
		fprintf(stderr, "Missing host:port specification.");
		print_usage(argv[0]);
	} else if ((optind + 1) < argc) {
		fprintf(stderr, "Missing host:port specification.");
		print_usage(argv[0]);
	} else {
		host = strdup(argv[optind]);
		port = strrchr(host, ':');
		if (port == NULL) {
			fprintf(stderr, "Missing port specification.");
			print_usage(argv[0]);
		}
		port[0] = '\0';
		port++;
	}

	char* const arg[] = {STUNNEL, "./stunnel.callhome.conf", NULL};

	openlog("callhome", LOG_PID, LOG_DAEMON);
	nc_callback_print(clb_print);
	nc_verbosity(NC_VERB_DEBUG);


	nc_session_transport(proto);
	srv = nc_callhome_mngmt_server_add(NULL, host, port);
	if (proto == NC_TRANSPORT_TLS) {
		pid = nc_callhome_connect(srv, 5, 3, STUNNEL, arg, &sock);
		//		pid = nc_callhome_connect(start_server, app->rec_interval, app->rec_count, stunnel_argv[0], stunnel_argv, &sock);
	} else {
		/* for SSH we don't need to specify specific arguments */
		pid = nc_callhome_connect(srv, 5, 3, NULL, NULL, &sock);
	}

	printf("Working in background...\n");
	retpid = waitpid(pid, &status, 0); // wait for child process to end
	if (retpid != pid) {
		if (retpid == -1) {
			printf("errno(%d) [%s]\n", errno, strerror(errno));
		} else {
			printf("pid != retpid (%d)\n", retpid);
			if (WIFCONTINUED(status)) {
				printf("WIFCONTINUED\n");
			}
			if (WIFEXITED(status)) {
				printf("WIFEXITED\n");
			}
			if (WIFSIGNALED(status)) {
				printf("WIFSIGNALED\n");
			}
			if (WIFSTOPPED(status)) {
				printf("WIFSTOPPED\n");
			}
		}
	}

	return (0);
}


void hexDebugDump (unsigned char *data, size_t size)
{
	unsigned char *p = data;
 	unsigned char c;
 	int n;
 	char bytestr[4] = {0};
 	char addrstr[10] = {0};
 	char hexstr[ 16*3 + 5] = {0};
 	char charstr[16*1 + 5] = {0};

    nc_verb_verbose(" START data:%p size:%d\n", data, size); 

 	for (n=1; n<=size; n++) {
  		if (n%16 == 1) { 
    		/* store address for this line */
    		snprintf (addrstr, sizeof(addrstr), "%.4x", ((unsigned int)p-(unsigned int)data) );
  		}
  		c = *p;
  		if (isalnum(c) == 0) {
   			c = '.';
  		}
  		/* store hex str (for left side) */
  		snprintf (bytestr, sizeof(bytestr), "%02X ", *p);
  		strncat ((char *)hexstr, (char *)bytestr, sizeof(hexstr)-strlen(hexstr)-1);
  		/* store char str (for right side) */
 		snprintf (bytestr, sizeof(bytestr), "%c", c);
  		strncat (charstr, bytestr, sizeof(charstr)-strlen(charstr)-1);
  		if (n%16 == 0) { 
   			/* line completed */
   			nc_verb_verbose("[%4.4s]   %-50.50s  %s\n",  addrstr,  hexstr,  charstr);
   			hexstr[0] = 0;
   			charstr[0] = 0;
  		}			 
		else if (n%8 == 0) {
   		/* half line: add whitespaces */
   			strncat (hexstr,  "  ",  sizeof(hexstr)-strlen(hexstr)-1);
   			strncat (charstr, " ",  sizeof(charstr)-strlen(charstr)-1);
  		}
  		p++; /* next byte */
 	}
 	if (strlen ((char *)hexstr) > 0) {
  		/* print rest of buffer if not empty */
  		nc_verb_verbose("[%4.4s]   %-50.50s  %s\n",  addrstr,  hexstr,  charstr);
 	}

    nc_verb_verbose(" END data:%p size:%d\n", data, size);

}

