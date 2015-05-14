/*
 * commands.c
 * Author Radek Krejci <rkrejci@cesnet.cz>
 *
 * Implementation of the NETCONF client commands.
 *
 * Copyright (C) 2012 CESNET, z.s.p.o.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 * 3. Neither the name of the Company nor the names of its contributors
 *    may be used to endorse or promote products derived from this
 *    software without specific prior written permission.
 *
 * ALTERNATIVELY, provided that this notice is retained in full, this
 * product may be distributed under the terms of the GNU General Public
 * License (GPL) version 2 or later, in which case the provisions
 * of the GPL apply INSTEAD OF those given above.
 *
 * This software is provided ``as is, and any express or implied
 * warranties, including, but not limited to, the implied warranties of
 * merchantability and fitness for a particular purpose are disclaimed.
 * In no event shall the company or contributors be liable for any
 * direct, indirect, incidental, special, exemplary, or consequential
 * damages (including, but not limited to, procurement of substitute
 * goods or services; loss of use, data, or profits; or business
 * interruption) however caused and on any theory of liability, whether
 * in contract, strict liability, or tort (including negligence or
 * otherwise) arising in any way out of the use of this software, even
 * if advised of the possibility of such damage.
 *
 */

#define _GNU_SOURCE
#include <stdarg.h>
#include <ctype.h>
#include <stdio.h>
#include <unistd.h>
#include <libnetconf.h>
#include <getopt.h>
#include <string.h>
#include <errno.h>
#include <sys/mman.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <sys/sendfile.h>
#include <fcntl.h>
#include <dirent.h>
#include <time.h>

#ifndef DISABLE_NOTIFICATIONS
#include <pthread.h>
#endif

#include <libnetconf.h>
#include <libnetconf_ssh.h>
#ifdef ENABLE_TLS
#	include <openssl/pem.h>
#	include <openssl/x509v3.h>
#	include <libnetconf_tls.h>
#endif

#include "commands.h"
#include "configuration.h"
#include "mreadline.h"

static const char rcsid[] __attribute__((used)) ="$Id: "__FILE__": "RCSID" $";

#define NC_CAP_CANDIDATE_ID "urn:ietf:params:netconf:capability:candidate:1.0"
#define NC_CAP_STARTUP_ID   "urn:ietf:params:netconf:capability:startup:1.0"
#define NC_CAP_ROLLBACK_ID  "urn:ietf:params:netconf:capability:rollback-on-error:1.0"
#define NC_CAP_VALIDATE10_ID  "urn:ietf:params:netconf:capability:validate:1.0"
#define NC_CAP_VALIDATE11_ID  "urn:ietf:params:netconf:capability:validate:1.1"
#define NC_CAP_WITHDEFAULTS_ID "urn:ietf:params:netconf:capability:with-defaults:1.0"
#define NC_CAP_URL_ID       "urn:ietf:params:netconf:capability:url:1.0"

extern int done;
extern struct nc_cpblts * client_supported_cpblts;
volatile int verb_level = 0;

void print_version ();

struct nc_session* session = NULL;

#define BUFFER_SIZE 1024

COMMAND commands[] = {
		{"help", cmd_help, "Display this text"},
		{"connect", cmd_connect, "Connect to a NETCONF server"},
#ifndef DISABLE_CALLHOME
		{"listen", cmd_listen, "Listen for a NETCONF Call Home"},
#endif
		{"disconnect", cmd_disconnect, "Disconnect from a NETCONF server"},
		{"commit", cmd_commit, "NETCONF <commit> operation"},
		{"copy-config", cmd_copyconfig, "NETCONF <copy-config> operation"},
		{"delete-config", cmd_deleteconfig, "NETCONF <delete-config> operation"},
		{"discard-changes", cmd_discardchanges, "NETCONF <discard-changes> operation"},
		{"edit-config", cmd_editconfig, "NETCONF <edit-config> operation"},
		{"get", cmd_get, "NETCONF <get> operation"},
		{"get-config", cmd_getconfig, "NETCONF <get-config> operation"},
		{"get-schema", cmd_getschema, "NETCONF <get-schema> operation"},
		{"kill-session", cmd_killsession, "NETCONF <kill-session> operation"},
		{"lock", cmd_lock, "NETCONF <lock> operation"},
		{"unlock", cmd_unlock, "NETCONF <unlock> operation"},
		{"validate", cmd_validate, "NETCONF <validate> operation"},
#ifndef DISABLE_NOTIFICATIONS
		{"subscribe", cmd_subscribe, "NETCONF Event Notifications <create-subscription> operation"},
#endif
#ifdef ENABLE_TLS
		{"cert", cmd_cert, "Manage trusted or your own certificates"},
		{"crl", cmd_crl, "Manage Certificate Revocation List directory"},
#endif
		{"status", cmd_status, "Print information about the current NETCONF session"},
		{"user-rpc", cmd_userrpc, "Send your own content in an RPC envelope (for DEBUG purposes)"},
		{"verbose", cmd_verbose, "Enable/disable verbose messages"},
		{"quit", cmd_quit, "Quit the program"},
		{"capability", cmd_capability, "Add/remove capability to/from the list of supported capabilities"},
/* synonyms for previous commands */
		{"debug", cmd_debug, NULL},
		{"?", cmd_help, NULL},
		{"exit", cmd_quit, NULL},
		{NULL, NULL, NULL}
};

char* cert_commands[] = {
		"display",
		"add",
		"remove",
		"displayown",
		"replaceown",
		NULL
};

char* crl_commands[] = {
		"display",
		"add",
		"remove",
		NULL
};

typedef enum GENERIC_OPS {
	GO_COMMIT,
	GO_DISCARD_CHANGES
} GENERIC_OPS;
int cmd_generic_op(GENERIC_OPS op, const char *arg);

struct arglist {
	char **list;
	int count;
	int size;
};

/**
 * \brief Initiate arglist to defined values
 *
 * \param args          pointer to the arglist structure
 * \return              0 if success, non-zero otherwise
 */
void init_arglist (struct arglist *args)
{
	if (args != NULL) {
		args->list = NULL;
		args->count = 0;
		args->size = 0;
	}
}

/**
 * \brief Clear arglist including free up allocated memory
 *
 * \param args          pointer to the arglist structure
 * \return              none
 */
void clear_arglist (struct arglist *args)
{
	int i = 0;

	if (args && args->list) {
		for (i = 0; i < args->count; i++) {
			if (args->list[i]) {
				free (args->list[i]);
			}
		}
		free (args->list);
	}

	init_arglist (args);
}

/**
 * \brief add arguments to arglist
 *
 * Adds erguments to arglist's structure. Arglist's list variable
 * is used to building execv() arguments.
 *
 * \param args          arglist to store arguments
 * \param format        arguments to add to the arglist
 */
void addargs (struct arglist *args, char *format, ...)
{
	va_list arguments;
	char *aux = NULL, *aux1 = NULL;
	int len;

	if (args == NULL)
	return;

	/* store arguments to aux string */
	va_start(arguments, format);
	if ((len = vasprintf(&aux, format, arguments)) == -1)
	perror("addargs - vasprintf");
	va_end(arguments);

	/* parse aux string and store it to the arglist */
	/* find \n and \t characters and replace them by space */
	while ((aux1 = strpbrk(aux, "\n\t")) != NULL) {
		*aux1 = ' ';
	}
	/* remember the begining of the aux string to free it after operations */
	aux1 = aux;

	/*
	 * get word by word from given string and store words separately into
	 * the arglist
	 */
	for (aux = strtok(aux, " "); aux != NULL; aux = strtok(NULL, " ")) {
		if (!strcmp(aux, ""))
		continue;

		if (args->list == NULL) { /* initial memory allocation */
			if ((args->list = (char **) malloc(8 * sizeof(char *))) == NULL)
			perror("Fatal error while allocating memory");
			args->size = 8;
			args->count = 0;
		} else if (args->count + 2 >= args->size) {
			/*
			 * list is too short to add next to word so we have to
			 * extend it
			 */
			args->size += 8;
			args->list = realloc(args->list, args->size * sizeof(char *));
		}
		/* add word in the end of the list */
		if ((args->list[args->count] = (char *) malloc((strlen(aux) + 1) * sizeof(char))) == NULL)
		perror("Fatal error while allocating memory");
		strcpy(args->list[args->count], aux);
		args->list[++args->count] = NULL; /* last argument */
	}
	/* clean up */
	free(aux1);
}

int cmd_status (const char* UNUSED(arg))
{
	const char *s;
	struct nc_cpblts* cpblts;

	if (session == NULL) {
		fprintf (stdout, "Client is not connected to any NETCONF server.\n");
	} else {
		fprintf (stdout, "Current NETCONF session:\n");
		fprintf (stdout, "  ID          : %s\n", nc_session_get_id(session));
		fprintf (stdout, "  Host        : %s\n", nc_session_get_host(session));
		fprintf (stdout, "  Port        : %s\n", nc_session_get_port(session));
		fprintf (stdout, "  User        : %s\n", nc_session_get_user(session));
		switch(nc_session_get_transport(session)) {
		case NC_TRANSPORT_SSH:
			s = "SSH";
			break;
		case NC_TRANSPORT_TLS:
			s = "TLS";
			break;
		default:
			s = "Unknown";
			break;
		}
		fprintf (stdout, "  Transport   : %s\n", s);
		fprintf (stdout, "  Capabilities:\n");
		cpblts = nc_session_get_cpblts (session);
		if (cpblts != NULL) {
			nc_cpblts_iter_start (cpblts);
			while ((s = nc_cpblts_iter_next (cpblts)) != NULL) {
				fprintf (stdout, "\t%s\n", s);
			}
		}
	}

	return (EXIT_SUCCESS);
}

static NC_DATASTORE get_datastore(const char* paramtype, const char* operation, struct arglist *cmd, int index, char** url)
{
	int valid = 0;
	char *datastore;
	NC_DATASTORE retval = NC_DATASTORE_ERROR;

	if (index == cmd->count) {

userinput:

		datastore = malloc (sizeof(char) * BUFFER_SIZE);
		if (datastore == NULL) {
			ERROR(operation, "memory allocation error (%s).", strerror (errno));
			return (NC_DATASTORE_ERROR);
		}

		/* repeat user input until valid datastore is selected */
		while (!valid) {
			/* get mandatory argument */
			INSTRUCTION("Select %s datastore (running", paramtype);
			if (nc_cpblts_enabled (session, NC_CAP_STARTUP_ID)) {
				fprintf (stdout, "|startup");
			}
			if (nc_cpblts_enabled (session, NC_CAP_CANDIDATE_ID)) {
				fprintf (stdout, "|candidate");
			}
			if (nc_cpblts_enabled (session, NC_CAP_URL_ID)) {
				fprintf (stdout, "|url:<dsturl>");
			}
			fprintf (stdout, "): ");
			if (scanf ("%1023s", datastore) == EOF) {
				ERROR(operation, "Reading the user input failed (%s).", (errno != 0) ? strerror(errno) : "Unexpected input");
				return (NC_DATASTORE_ERROR);
			}

			/* validate argument */
			if (strcmp (datastore, "running") == 0) {
				valid = 1;
				retval = NC_DATASTORE_RUNNING;
			}
			if (nc_cpblts_enabled (session, NC_CAP_STARTUP_ID) && strcmp (datastore, "startup") == 0) {
				valid = 1;
				retval = NC_DATASTORE_STARTUP;
			}
			if (nc_cpblts_enabled (session, NC_CAP_CANDIDATE_ID) && strcmp (datastore, "candidate") == 0) {
				valid = 1;
				retval = NC_DATASTORE_CANDIDATE;
			}
			if (nc_cpblts_enabled (session, NC_CAP_URL_ID) && strncmp (datastore, "url:", 4) == 0) {
				valid = 1;
				retval = NC_DATASTORE_URL;
				if (url != NULL) {
					*url = strdup(&(datastore[4]));
				}
			}

			if (!valid) {
				ERROR(operation, "invalid %s datastore type.", paramtype);
			} else {
				free(datastore);
			}
		}
	} else if ((index + 1) == cmd->count) {
		datastore = cmd->list[index];

		/* validate argument */
		if (strcmp (datastore, "running") == 0) {
			valid = 1;
			retval = NC_DATASTORE_RUNNING;
		}
		if (nc_cpblts_enabled (session, NC_CAP_STARTUP_ID) && strcmp (datastore, "startup") == 0) {
			valid = 1;
			retval = NC_DATASTORE_STARTUP;
		}
		if (nc_cpblts_enabled (session, NC_CAP_CANDIDATE_ID) && strcmp (datastore, "candidate") == 0) {
			valid = 1;
			retval = NC_DATASTORE_CANDIDATE;
		}
		if (nc_cpblts_enabled (session, NC_CAP_URL_ID) && strncmp (datastore, "url:", 4) == 0) {
			valid = 1;
			retval = NC_DATASTORE_URL;
			if (url != NULL) {
				*url = strdup(&(datastore[4]));
			}
		}

		if (!valid) {
			goto userinput;
		}
	} else {
		ERROR(operation, "invalid parameters, see \'%s --help\'.", operation);
		return (NC_DATASTORE_ERROR);
	}

	return (retval);
}
static NCWD_MODE get_withdefaults(const char* operation, const char* mode)
{
	NCWD_MODE retval = NCWD_MODE_NOTSET;
	char* mode_aux;
	mode_aux = malloc(sizeof(char) * 128);

	if (0) {
userinput:
		/* get mandatory argument */
		INSTRUCTION("Select a with-defaults mode (report-all|report-all-tagged|trim|explicit): ");
		if (scanf ("%127s", mode_aux) == EOF) {
			ERROR(operation, "Reading user input failed (%s).", (errno != 0) ? strerror(errno) : "Unexpected input");
			return (NCWD_MODE_NOTSET);
		}
		mode = mode_aux;
	}

	if (mode != NULL) {
		if (strcmp(mode, "report-all") == 0) {
			retval = NCWD_MODE_ALL;
		} else if (strcmp(mode, "report-all-tagged") == 0) {
			retval = NCWD_MODE_ALL_TAGGED;
		} else if (strcmp(mode, "trim") == 0) {
			retval = NCWD_MODE_TRIM;
		} else if (strcmp(mode, "explicit") == 0) {
			retval = NCWD_MODE_EXPLICIT;
		} else {
			goto userinput;
		}
	}

	free(mode_aux);
	return (retval);
}

static struct nc_filter *set_filter(const char* operation, const char *file)
{
	int filter_fd;
	struct stat filter_stat;
	char *filter_s;
	struct nc_filter *filter = NULL;

	if (operation == NULL) {
		return (NULL);
	}

	if (file) {
		/* open filter from the file */
		filter_fd = open(optarg, O_RDONLY);
		if (filter_fd == -1) {
			ERROR(operation, "unable to open the filter file (%s).", strerror(errno));
			return (NULL);
		}

		/* map content of the file into the memory */
		fstat(filter_fd, &filter_stat);
		filter_s = (char*) mmap(NULL, filter_stat.st_size, PROT_READ, MAP_PRIVATE, filter_fd, 0);
		if (filter_s == MAP_FAILED) {
			ERROR(operation, "mmapping of the filter file failed (%s).", strerror(errno));
			close(filter_fd);
			return (NULL);
		}

		/* create the filter according to the file content */
		filter = nc_filter_new(NC_FILTER_SUBTREE, filter_s);

		/* unmap filter file and close it */
		munmap(filter_s, filter_stat.st_size);
		close(filter_fd);
	} else {
		/* let user write filter interactively */
		INSTRUCTION("Type the filter (close editor by Ctrl-D):\n");
		filter_s = mreadline(NULL);

		/* create the filter according to the file content */
		filter = nc_filter_new(NC_FILTER_SUBTREE, filter_s);

		/* cleanup */
		free(filter_s);
	}

	return (filter);
}

/* rpc parameter is freed after the function call */
static int send_recv_process(const char* operation, nc_rpc* rpc)
{
	nc_reply *reply = NULL;
	char *data = NULL;
	int ret = EXIT_SUCCESS;

	/* send the request and get the reply */
	switch (nc_session_send_recv(session, rpc, &reply)) {
	case NC_MSG_UNKNOWN:
		if (nc_session_get_status(session) != NC_SESSION_STATUS_WORKING) {
			ERROR(operation, "receiving rpc-reply failed.");
			INSTRUCTION("Closing the session.\n");
			cmd_disconnect(NULL);
			ret = EXIT_FAILURE;
			break;
		}
		ERROR(operation, "Unknown error occurred.");
		ret = EXIT_FAILURE;
		break;
	case NC_MSG_NONE:
		/* error occurred, but processed by callback */
		break;
	case NC_MSG_REPLY:
		switch (nc_reply_get_type(reply)) {
		case NC_REPLY_OK:
			INSTRUCTION("Result OK\n");
			break;
		case NC_REPLY_DATA:
			INSTRUCTION("Result:\n");
			fprintf(stdout, "%s\n", data = nc_reply_get_data (reply));
			free(data);
			break;
		case NC_REPLY_ERROR:
			/* wtf, you shouldn't be here !?!? */
			ERROR(operation, "operation failed, but rpc-error was not processed.");
			ret = EXIT_FAILURE;
			break;
		default:
			ERROR(operation, "unexpected operation result.");
			ret = EXIT_FAILURE;
			break;
		}
		break;
	default:
		ERROR(operation, "Unknown error occurred.");
		ret = EXIT_FAILURE;
		break;
	}
	nc_rpc_free(rpc);
	nc_reply_free(reply);

	return (ret);
}

void cmd_editconfig_help()
{
	char *rollback;
	char *validate;

	if (session == NULL || nc_cpblts_enabled (session, NC_CAP_ROLLBACK_ID)) {
		rollback = "|rollback";
	} else {
		rollback = "";
	}

	if (session == NULL || nc_cpblts_enabled (session, NC_CAP_VALIDATE11_ID)) {
			validate = "[--test <set|test-only|test-then-set>] ";
	} else if (session == NULL || nc_cpblts_enabled (session, NC_CAP_VALIDATE10_ID)) {
		validate = "[--test <set|test-then-set>] ";
	} else {
		validate = "";
	}

	/* if session not established, print complete help for all capabilities */
	fprintf (stdout, "edit-config [--help] [--defop <merge|replace|none>] [--error <stop|continue%s>] %s[--config <file> | --url <url>] running", rollback, validate);
	if (session == NULL || nc_cpblts_enabled (session, NC_CAP_STARTUP_ID)) {
		fprintf (stdout, "|startup");
	}
	if (session == NULL || nc_cpblts_enabled (session, NC_CAP_CANDIDATE_ID)) {
		fprintf (stdout, "|candidate");
	}
	fprintf (stdout, "\nIf neither --config nor --url is specified, user is prompted to set edit data manually.\n");
}

int cmd_editconfig (const char *arg)
{
	int c;
	char *config_m = NULL, *config = NULL;
	int config_fd;
	struct stat config_stat;
	NC_DATASTORE target, source = NC_DATASTORE_ERROR;
	NC_EDIT_DEFOP_TYPE defop = 0; /* do not set this parameter by default */
	NC_EDIT_ERROPT_TYPE erropt = 0; /* do not set this parameter by default */
	NC_EDIT_TESTOPT_TYPE testopt = 0;
	nc_rpc *rpc = NULL;
	struct arglist cmd;
	struct option long_options[] ={
			{"config", 1, 0, 'c'},
			{"defop", 1, 0, 'd'},
			{"error", 1, 0, 'e'},
			{"help", 0, 0, 'h'},
			{"test", 1, 0, 't'},
			{"url", 1, 0, 'u'},
			{0, 0, 0, 0}
	};
	int option_index = 0;

	/* set back to start to be able to use getopt() repeatedly */
	optind = 0;

	if (session == NULL) {
		ERROR("get", "NETCONF session not established, use \'connect\' command.");
		return (EXIT_FAILURE);
	}

	init_arglist (&cmd);
	addargs (&cmd, "%s", arg);

	/* rocess command line parameters */
	while ((c = getopt_long (cmd.count, cmd.list, "c:d:e:t:u:h", long_options, &option_index)) != -1) {
		switch (c) {
		case 'c':
			/* check if -u was not used */
			if (config != NULL) {
				ERROR("edit-config", "mixing --config and --url parameters is not allowed.");
				clear_arglist(&cmd);
				return (EXIT_FAILURE);
			}

			/* open edit configuration data from the file */
			config_fd = open(optarg, O_RDONLY);
			if (config_fd == -1) {
				ERROR("edit-config", "unable to open the edit data file (%s).", strerror(errno));
				clear_arglist(&cmd);
				return (EXIT_FAILURE);
			}

			/* map content of the file into the memory */
			fstat(config_fd, &config_stat);
			config_m = (char*) mmap(NULL, config_stat.st_size, PROT_READ, MAP_PRIVATE, config_fd, 0);
			if (config_m == MAP_FAILED) {
				ERROR("edit-config", "mmapping of the edit data file failed (%s).", strerror(errno));
				clear_arglist(&cmd);
				close(config_fd);
				return (EXIT_FAILURE);
			}

			/* make a copy of the content to allow closing the file */
			config = strdup(config_m);
			source = NC_DATASTORE_CONFIG;

			/* unmap edit data file and close it */
			munmap(config_m, config_stat.st_size);
			close(config_fd);

			break;
		case 'd':
			/* validate default operation */
			if (strcmp (optarg, "merge") == 0) {
				defop = NC_EDIT_DEFOP_MERGE;
			} else if (strcmp (optarg, "replace") == 0) {
				defop = NC_EDIT_DEFOP_REPLACE;
			} else if (strcmp (optarg, "none") == 0) {
				defop = NC_EDIT_DEFOP_NONE;
			} else {
				ERROR("edit-config", "invalid default operation %s.", optarg);
				cmd_editconfig_help ();
				clear_arglist(&cmd);
				return (EXIT_FAILURE);
			}

			break;
		case 'e':
			/* validate error option */
			if (strcmp (optarg, "stop") == 0) {
				erropt = NC_EDIT_ERROPT_STOP;
			} else if (strcmp (optarg, "continue") == 0) {
				erropt = NC_EDIT_ERROPT_CONT;
			} else if (nc_cpblts_enabled (session, NC_CAP_ROLLBACK_ID) && strcmp (optarg, "rollback") == 0) {
				erropt = NC_EDIT_ERROPT_ROLLBACK;
			} else {
				ERROR("edit-config", "invalid error-option %s.", optarg);
				cmd_editconfig_help ();
				clear_arglist(&cmd);
				return (EXIT_FAILURE);
			}

			break;
		case 'h':
			cmd_editconfig_help ();
			clear_arglist(&cmd);
			return (EXIT_SUCCESS);
			break;
		case 't':
			if (!(nc_cpblts_enabled (session, NC_CAP_VALIDATE11_ID) || nc_cpblts_enabled (session, NC_CAP_VALIDATE10_ID))) {
				ERROR("edit-config", "test-option is not allowed by the current session");
				cmd_editconfig_help ();
				clear_arglist(&cmd);
				return (EXIT_FAILURE);
			}
			/* validate test option */
			if (strcmp (optarg, "set") == 0) {
				testopt = NC_EDIT_TESTOPT_SET;
			} else if (strcmp (optarg, "test-only") == 0 && nc_cpblts_enabled (session, NC_CAP_VALIDATE11_ID)) {
				testopt = NC_EDIT_TESTOPT_TEST;
			} else if (strcmp (optarg, "test-then-set") == 0) {
				testopt = NC_EDIT_TESTOPT_TESTSET;
			} else {
				ERROR("edit-config", "invalid test-option %s.", optarg);
				cmd_editconfig_help ();
				clear_arglist(&cmd);
				return (EXIT_FAILURE);
			}

			break;
		case 'u':
			/* check if -c was not used */
			if (config != NULL) {
				ERROR("edit-config", "mixing --config and --url parameters is not allowed.");
				clear_arglist(&cmd);
				return (EXIT_FAILURE);
			}
			config = strdup(optarg);
			source = NC_DATASTORE_URL;
			break;
		default:
			ERROR("edit-config", "unknown option -%c.", c);
			cmd_editconfig_help ();
			clear_arglist(&cmd);
			return (EXIT_FAILURE);
		}
	}

	/* get what datastore is target of the operation */
	target = get_datastore("target", "edit-config", &cmd, optind, NULL);

	/* arglist is no more needed */
	clear_arglist(&cmd);

	if (target == NC_DATASTORE_ERROR) {
		return (EXIT_FAILURE);
	}

	/* check if edit configuration data were specified */
	if (config == NULL) {
		/* let user write edit data interactively */
		INSTRUCTION("Type the edit configuration data (close editor by Ctrl-D):\n");
		config = mreadline(NULL);
		if (config == NULL) {
			ERROR("edit-config", "reading edit data failed.");
			return (EXIT_FAILURE);
		}
		source = NC_DATASTORE_CONFIG;
	}

	/* create requests */
	rpc = nc_rpc_editconfig(target, source, defop, erropt, testopt, config);
	free(config);
	if (rpc == NULL) {
		ERROR("edit-config", "creating rpc request failed.");
		return (EXIT_FAILURE);
	}

	/* send the request and get the reply */
	return (send_recv_process("edit-config", rpc));
}

void cmd_validate_help ()
{
	char *ds_startup, *ds_candidate, *ds_url;

	if (session == NULL) {
		/* if session not established, print complete help for all capabilities */
		ds_startup = "|startup";
		ds_candidate = "|candidate";
		ds_url = "|url:<url>";
	} else {
		if (nc_cpblts_enabled (session, NC_CAP_STARTUP_ID)) {
			ds_startup = "|startup";
		} else {
			ds_startup = "";
		}
		if (nc_cpblts_enabled (session, NC_CAP_CANDIDATE_ID)) {
			ds_candidate = "|candidate";
		} else {
			ds_candidate = "";
		}
		if (nc_cpblts_enabled (session, NC_CAP_URL_ID)) {
			ds_url = "|url:<dsturl>";
		} else {
			ds_url = "";
		}
	}
	fprintf (stdout, "validate [--help] --config [<file>] | running%s%s%s\n",
			ds_startup, ds_candidate, ds_url);

	if (session != NULL &&
	    !(nc_cpblts_enabled (session, NC_CAP_VALIDATE10_ID) || nc_cpblts_enabled (session, NC_CAP_VALIDATE11_ID))) {
		fprintf (stdout, "WARNING: validate operation is not supported in the current session.\n");
	}
}

int cmd_validate (const char *arg)
{
	int c;
	int config_fd;
	struct stat config_stat;
	char *config = NULL, *config_m = NULL;
	NC_DATASTORE source = NC_DATASTORE_ERROR;
	nc_rpc *rpc = NULL;
	struct arglist cmd;
	struct option long_options[] ={
			{"config", 2, 0, 'c'},
			{"help", 0, 0, 'h'},
			{0, 0, 0, 0}
	};
	int option_index = 0;

	/* set back to start to be able to use getopt() repeatedly */
	optind = 0;

	if (session == NULL) {
		ERROR("validate", "NETCONF session not established, use \'connect\' command.");
		return (EXIT_FAILURE);
	}

	init_arglist (&cmd);
	addargs (&cmd, "%s", arg);

	while ((c = getopt_long (cmd.count, cmd.list, "c::h", long_options, &option_index)) != -1) {
		switch (c) {
		case 'c':
			if (optarg == NULL) {
				/* let user write edit data interactively */
				INSTRUCTION("Type the content of a configuration datastore (close editor by Ctrl-D):\n");
				config = mreadline(NULL);
				if (config == NULL) {
					ERROR("validate", "reading configuration data failed.");
					return (EXIT_FAILURE);
				}
			} else {
				/* open configuration data from the file */
				config_fd = open(optarg, O_RDONLY);
				if (config_fd == -1) {
					ERROR("validate", "unable to open the local datastore file (%s).", strerror(errno));
					clear_arglist(&cmd);
					return (EXIT_FAILURE);
				}

				/* map content of the file into the memory */
				fstat(config_fd, &config_stat);
				config_m = (char*) mmap(NULL, config_stat.st_size, PROT_READ, MAP_PRIVATE, config_fd, 0);
				if (config_m == MAP_FAILED) {
					ERROR("validate", "mmapping of the local datastore file failed (%s).", strerror(errno));
					clear_arglist(&cmd);
					close(config_fd);
					return (EXIT_FAILURE);
				}

				/* make a copy of the content to allow closing the file */
				config = strdup(config_m);

				/* unmap local datastore file and close it */
				munmap(config_m, config_stat.st_size);
				close(config_fd);
			}

			source = NC_DATASTORE_CONFIG;
			break;
		case 'h':
			cmd_validate_help ();
			clear_arglist(&cmd);
			return (EXIT_SUCCESS);
			break;
		default:
			ERROR("validate", "unknown option -%c.", c);
			cmd_validate_help ();
			clear_arglist(&cmd);
			return (EXIT_FAILURE);
		}
	}

	/* if the config option not set, parse remaining arguments to get source */
	if (config == NULL) {
		source = get_datastore("source", "validate", &cmd, optind, &config);
	}

	/* arglist is no more needed */
	clear_arglist(&cmd);

	/* create requests */
	rpc = nc_rpc_validate (source, config);
	free(config);
	if (rpc == NULL) {
		ERROR("validate", "creating an rpc request failed.");
		return (EXIT_FAILURE);
	}

	/* send the request and get the reply */
	return (send_recv_process("validate", rpc));
}

void cmd_copyconfig_help ()
{
	char *ds_startup, *ds_candidate, *ds_url;
	char *defaults;

	if (session == NULL) {
		/* if session not established, print complete help for all capabilities */
		ds_startup = "|startup";
		ds_candidate = "|candidate";
		ds_url = "|url:<dsturl>";
		defaults = "[--defaults report-all|report-all-tagged|trim|explicit] ";
	} else {
		if (nc_cpblts_enabled (session, NC_CAP_STARTUP_ID)) {
			ds_startup = "|startup";
		} else {
			ds_startup = "";
		}
		if (nc_cpblts_enabled (session, NC_CAP_CANDIDATE_ID)) {
			ds_candidate = "|candidate";
		} else {
			ds_candidate = "";
		}
		if (nc_cpblts_enabled (session, NC_CAP_URL_ID)) {
			ds_url = "|url:<dsturl>";
		} else {
			ds_url = "";
		}

		if (nc_cpblts_enabled (session, NC_CAP_WITHDEFAULTS_ID)) {
			defaults = "[--defaults report-all|report-all-tagged|trim|explicit] ";
		} else {
			defaults = "";
		}
	}

	fprintf (stdout, "copy-config [--help] %s[--source running%s%s%s | --config <file>] running%s%s%s\n",
			defaults, ds_startup, ds_candidate, ds_url,
			ds_startup, ds_candidate, ds_url);
}

int cmd_copyconfig (const char *arg)
{
	int c;
	int config_fd;
	struct stat config_stat;
	char *config = NULL, *config_m = NULL, *url_dst = NULL;
	NC_DATASTORE target;
	NC_DATASTORE source = NC_DATASTORE_ERROR;
	struct nc_filter *filter = NULL;
	nc_rpc *rpc = NULL;
	NCWD_MODE wd = NCWD_MODE_NOTSET;
	struct arglist cmd;
	struct option long_options[] ={
			{"config", 1, 0, 'c'},
			{"defaults", 1, 0, 'd'},
			{"source", 1, 0, 's'},
			{"help", 0, 0, 'h'},
			{0, 0, 0, 0}
	};
	int option_index = 0;

	/* set back to start to be able to use getopt() repeatedly */
	optind = 0;

	if (session == NULL) {
		ERROR("copy-config", "NETCONF session not established, use \'connect\' command.");
		return (EXIT_FAILURE);
	}

	init_arglist (&cmd);
	addargs (&cmd, "%s", arg);

	while ((c = getopt_long (cmd.count, cmd.list, "c:d:s:u:h", long_options, &option_index)) != -1) {
		switch (c) {
		case 'c':
			/* check if -s was not used */
			if (source != NC_DATASTORE_ERROR) {
				ERROR("copy-config", "mixing --source, --url and --config parameters is not allowed.");
				clear_arglist(&cmd);
				return (EXIT_FAILURE);
			}

			/* open edit configuration data from the file */
			config_fd = open(optarg, O_RDONLY);
			if (config_fd == -1) {
				ERROR("copy-config", "unable to open the local datastore file (%s).", strerror(errno));
				clear_arglist(&cmd);
				return (EXIT_FAILURE);
			}

			/* map content of the file into the memory */
			fstat(config_fd, &config_stat);
			config_m = (char*) mmap(NULL, config_stat.st_size, PROT_READ, MAP_PRIVATE, config_fd, 0);
			if (config_m == MAP_FAILED) {
				ERROR("copy-config", "mmapping of the local datastore file failed (%s).", strerror(errno));
				clear_arglist(&cmd);
				close(config_fd);
				return (EXIT_FAILURE);
			}

			/* make a copy of the content to allow closing the file */
			config = strdup(config_m);
			source = NC_DATASTORE_CONFIG;

			/* unmap local datastore file and close it */
			munmap(config_m, config_stat.st_size);
			close(config_fd);
			break;
		case 'd':
			wd = get_withdefaults("get-config", optarg);
			break;
		case 's':
			/* check if -c was not used */
			if (source != NC_DATASTORE_ERROR) {
				ERROR("copy-config", "mixing --source, --url and --config parameters is not allowed.");
				clear_arglist(&cmd);
				return (EXIT_FAILURE);
			}

			/* validate argument */
			if (strcmp (optarg, "running") == 0) {
				source = NC_DATASTORE_RUNNING;
			}
			if (nc_cpblts_enabled (session, NC_CAP_STARTUP_ID) && strcmp (optarg, "startup") == 0) {
				source = NC_DATASTORE_STARTUP;
			}
			if (nc_cpblts_enabled (session, NC_CAP_CANDIDATE_ID) && strcmp (optarg, "candidate") == 0) {
				source = NC_DATASTORE_CANDIDATE;
			}
			if (nc_cpblts_enabled (session, NC_CAP_URL_ID) && strncmp (optarg, "url:", 4) == 0) {
				source = NC_DATASTORE_URL;
				config = strdup(&(optarg[4]));
			}

			if (source == NC_DATASTORE_ERROR) {
				ERROR("copy-config", "invalid source datastore specified (%s).", optarg);
				clear_arglist(&cmd);
				return (EXIT_FAILURE);
			}
			break;
		case 'h':
			cmd_copyconfig_help ();
			clear_arglist(&cmd);
			return (EXIT_SUCCESS);
			break;
		default:
			ERROR("copy-config", "unknown option -%c.", c);
			cmd_copyconfig_help ();
			clear_arglist(&cmd);
			return (EXIT_FAILURE);
		}
	}

	target = get_datastore("target", "copy-config", &cmd, optind, &url_dst);

	/* arglist is no more needed */
	clear_arglist(&cmd);

	if (target == NC_DATASTORE_ERROR) {
		return (EXIT_FAILURE);
	}

	/* check if edit configuration data were specified */
	if (source == NC_DATASTORE_ERROR && config == NULL) {
		/* let user write edit data interactively */
		INSTRUCTION("Type the content of a configuration datastore (close editor by Ctrl-D):\n");
		config = mreadline(NULL);
		if (config == NULL) {
			ERROR("copy-config", "reading configuration data failed.");
			return (EXIT_FAILURE);
		}
		source = NC_DATASTORE_CONFIG;
	}

	/* create requests */
	if (config != NULL) {
		rpc = nc_rpc_copyconfig(source, target, config, url_dst);
	} else {
		rpc = nc_rpc_copyconfig(source, target, url_dst);
	}
	nc_filter_free(filter);
	free(config);
	free(url_dst);
	if (rpc == NULL) {
		ERROR("copy-config", "creating an rpc request failed.");
		return (EXIT_FAILURE);
	}
	/* set with defaults settings */
	if (nc_rpc_capability_attr(rpc, NC_CAP_ATTR_WITHDEFAULTS_MODE, wd) != EXIT_SUCCESS) {
		ERROR("copy-config", "setting up the with-defaults mode failed.");
		nc_rpc_free(rpc);
		return (EXIT_FAILURE);
	}

	/* send the request and get the reply */
	return (send_recv_process("copy-config", rpc));
}

void cmd_get_help ()
{
	char *defaults;

	if (session == NULL || nc_cpblts_enabled (session, NC_CAP_WITHDEFAULTS_ID)) {
		defaults = "[--defaults report-all|report-all-tagged|trim|explicit] ";
	} else {
		defaults = "";
	}
	fprintf (stdout, "get [--help] %s[--filter [file]]\n", defaults);
}


int cmd_get (const char *arg)
{
	int c;
	struct nc_filter *filter = NULL;
	nc_rpc *rpc = NULL;
	NCWD_MODE wd = NCWD_MODE_NOTSET;
	struct arglist cmd;
	struct option long_options[] ={
			{"defaults", 1, 0, 'd'},
			{"filter", 2, 0, 'f'},
			{"help", 0, 0, 'h'},
			{0, 0, 0, 0}
	};
	int option_index = 0;

	/* set back to start to be able to use getopt() repeatedly */
	optind = 0;

	if (session == NULL) {
		ERROR("get", "NETCONF session not established, use \'connect\' command.");
		return (EXIT_FAILURE);
	}

	init_arglist (&cmd);
	addargs (&cmd, "%s", arg);

	while ((c = getopt_long (cmd.count, cmd.list, "d:f::h", long_options, &option_index)) != -1) {
		switch (c) {
		case 'd':
			wd = get_withdefaults("get-config", optarg);
			break;
		case 'f':
			filter = set_filter("get", optarg);
			if (filter == NULL) {
				clear_arglist(&cmd);
				return (EXIT_FAILURE);
			}
			break;
		case 'h':
			cmd_get_help ();
			clear_arglist(&cmd);
			return (EXIT_SUCCESS);
			break;
		default:
			ERROR("get", "unknown option -%c.", c);
			cmd_get_help ();
			clear_arglist(&cmd);
			return (EXIT_FAILURE);
		}
	}

	if (optind < cmd.count) {
		ERROR("get", "invalid parameters, see \'get --help\'.");
		clear_arglist(&cmd);
		return (EXIT_FAILURE);
	}

	/* arglist is no more needed */
	clear_arglist(&cmd);

	/* create requests */
	rpc = nc_rpc_get (filter);
	nc_filter_free(filter);
	if (rpc == NULL) {
		ERROR("get", "creating an rpc request failed.");
		return (EXIT_FAILURE);
	}
	/* set with defaults settings */
	if (nc_rpc_capability_attr(rpc, NC_CAP_ATTR_WITHDEFAULTS_MODE, wd) != EXIT_SUCCESS) {
		ERROR("get", "setting up with-defaults mode failed.");
		nc_rpc_free(rpc);
		return (EXIT_FAILURE);
	}

	/* send the request and get the reply */
	return (send_recv_process("get", rpc));
}

void cmd_deleteconfig_help ()
{
	char *ds_startup, *ds_candidate, *ds_url;

	if (session == NULL) {
		ds_startup = "startup";
		ds_candidate = "|candidate";
		ds_url = "url:<url>";
	} else {
		if (nc_cpblts_enabled (session, NC_CAP_STARTUP_ID)) {
			ds_startup = "startup";
		} else {
			ds_startup = "";
		}

		if (nc_cpblts_enabled (session, NC_CAP_CANDIDATE_ID)) {
			ds_candidate = (strlen(ds_startup) == 0) ? "candidate" : "|candidate";
		} else {
			ds_candidate = "";
		}

		if (nc_cpblts_enabled (session, NC_CAP_URL_ID)) {
			ds_url = ((strlen(ds_startup) + strlen(ds_candidate)) == 0) ? "url:<url>" : "|url:<url>";
		} else {
			ds_url = "";
		}
	}

	if ((strlen(ds_startup) + strlen(ds_candidate) + strlen(ds_url)) == 0) {
		fprintf (stdout, "delete-config cannot be used in the current session.\n");
		return;
	}

	fprintf (stdout, "delete-config [--help]  %s%s%s\n", ds_startup, ds_candidate, ds_url);
}

int cmd_deleteconfig (const char *arg)
{
	int c;
	NC_DATASTORE target;
	nc_rpc *rpc = NULL;
	char *url = NULL;
	struct arglist cmd;
	struct option long_options[] ={
			{"help", 0, 0, 'h'},
			{0, 0, 0, 0}
	};
	int option_index = 0;

	/* set back to start to be able to use getopt() repeatedly */
	optind = 0;

	if (session == NULL) {
		ERROR("delete-config", "NETCONF session not established, use \'connect\' command.");
		return (EXIT_FAILURE);
	}

	if (!nc_cpblts_enabled (session, NC_CAP_STARTUP_ID) && !nc_cpblts_enabled (session, NC_CAP_CANDIDATE_ID)) {
		ERROR ("delete-config", "operation cannot be used in the current session.");
		return (EXIT_FAILURE);
	}

	init_arglist (&cmd);
	addargs (&cmd, "%s", arg);

	while ((c = getopt_long (cmd.count, cmd.list, "h", long_options, &option_index)) != -1) {
		switch (c) {
		case 'h':
			cmd_deleteconfig_help ();
			clear_arglist(&cmd);
			return (EXIT_SUCCESS);
			break;
		default:
			ERROR("delete-config", "unknown option -%c.", c);
			cmd_deleteconfig_help ();
			clear_arglist(&cmd);
			return (EXIT_FAILURE);
		}
	}

	target = get_datastore("target", "delete-config", &cmd, optind, &url);
	while (target == NC_DATASTORE_RUNNING) {
		fprintf (stdout, "delete-config: <running> datastore cannot be deleted.");
		target = get_datastore("target", "delete-config", &cmd, cmd.count, &url);
	}

	/* arglist is no more needed */
	clear_arglist(&cmd);

	if (target == NC_DATASTORE_ERROR) {
		return (EXIT_FAILURE);
	}

	/* create requests */
	rpc = nc_rpc_deleteconfig(target, url);
	free(url);
	if (rpc == NULL) {
		ERROR("delete-config", "creating an rpc request failed.");
		return (EXIT_FAILURE);
	}

	/* send the request and get the reply */
	return (send_recv_process("delete-config", rpc));
}

void cmd_killsession_help ()
{
	fprintf (stdout, "kill-session [--help] <sessionID>\n");
}

int cmd_killsession (const char *arg)
{
	int c;
	char *id;
	nc_rpc *rpc = NULL;
	struct arglist cmd;
	struct option long_options[] ={
			{"help", 0, 0, 'h'},
			{0, 0, 0, 0}
	};
	int option_index = 0;

	/* set back to start to be able to use getopt() repeatedly */
	optind = 0;

	if (session == NULL) {
		ERROR("kill-session", "NETCONF session not established, use \'connect\' command.");
		return (EXIT_FAILURE);
	}

	init_arglist (&cmd);
	addargs (&cmd, "%s", arg);

	while ((c = getopt_long (cmd.count, cmd.list, "h", long_options, &option_index)) != -1) {
		switch (c) {
		case 'h':
			cmd_killsession_help ();
			clear_arglist(&cmd);
			return (EXIT_SUCCESS);
			break;
		default:
			ERROR("kill-session", "unknown option -%c.", c);
			cmd_killsession_help ();
			clear_arglist(&cmd);
			return (EXIT_FAILURE);
		}
	}

	if ((optind + 1) == cmd.count) {
		id = strdup(cmd.list[optind]);
	} else {
		id = malloc (sizeof(char) * BUFFER_SIZE);
		if (id == NULL) {
			ERROR("kill-session", "memory allocation error (%s).", strerror (errno));
			clear_arglist(&cmd);
			return (EXIT_FAILURE);
		}
		id[0] = 0;

		while (id[0] == '\0') { /* string is empty */
			/* get mandatory argument */
			INSTRUCTION("Set session ID to kill: ");
			if (scanf ("%1023s", id) == EOF) {
				ERROR("kill-session", "Reading the user input failed (%s).", (errno != 0) ? strerror(errno) : "Unexpected input");
				clear_arglist(&cmd);
				return (EXIT_FAILURE);
			}
		}
	}

	/* arglist is no more needed */
	clear_arglist(&cmd);

	/* create requests */
	rpc = nc_rpc_killsession(id);
	free(id);
	if (rpc == NULL) {
		ERROR("kill-session", "creating an rpc request failed.");
		return (EXIT_FAILURE);
	}

	/* send the request and get the reply */
	return (send_recv_process("kell-session", rpc));
}

#define CAP_ADD 'a'
#define CAP_REM 'r'
#define CAP_LIST 'l'
#define CAP_DEF 'd'
void cmd_capability_help (void)
{
	fprintf (stdout, "capability {--help|--add <uri>|--rem {<uri>|*}|--list|--default\n");
}

int cmd_capability (const char * arg)
{
	struct arglist cmd;
	int c = -1;
	const char * uri;
	struct option long_options[] = {
			{"add", 1, 0, CAP_ADD},
			{"rem", 1, 0, CAP_REM},
			{"list", 0, 0, CAP_LIST},
			{"default", 0, 0, CAP_DEF},
			{"help", 0, 0, 'h'},
			{0, 0, 0, 0}
	};
	int option_index = 0;
	optind = 0;

	if (session != NULL) {
		ERROR ("capability", "NETCONF session already established. Any changes to the supported capability list will take effect after reconnection.");
	}

	init_arglist (&cmd);
	addargs (&cmd, "%s", arg);

	if ((c=getopt_long (cmd.count, cmd.list, "a:r:l:d:h", long_options, &option_index)) == -1) {
		cmd_capability_help();
	} else do {
		switch (c) {
		case 'h':
			cmd_capability_help ();
			clear_arglist(&cmd);
			return (EXIT_SUCCESS);
			break;
		case CAP_ADD:
			/* if adding first */
			if (client_supported_cpblts == NULL) {
				/* create structure */
				client_supported_cpblts = nc_cpblts_new (NULL);
			}
			if (nc_cpblts_add (client_supported_cpblts, optarg)) {
				ERROR ("capability", "Cannot add the capability \"%s\" to the client supported list.", optarg);
				return EXIT_FAILURE;
			}
			store_config (client_supported_cpblts);
			break;
		case CAP_REM:
			if (strcmp(optarg, "*") == 0) {
				nc_cpblts_free(client_supported_cpblts);
				client_supported_cpblts = nc_cpblts_new(NULL);
			} else {
				if (nc_cpblts_remove (client_supported_cpblts, optarg)) {
					ERROR ("capability", "Cannot remove the capability \"%s\" from the client supported list.", optarg);
					return EXIT_FAILURE;
				}
			}
			if (nc_cpblts_get(client_supported_cpblts, "urn:ietf:params:netconf:base:1.0") == NULL &&
					nc_cpblts_get(client_supported_cpblts, "urn:ietf:params:netconf:base:1.1") == NULL) {
				ERROR ("capability", "No base capability left in the supported list. Connection to a server will be impossible.");
			}
			store_config (client_supported_cpblts);
			break;
		case CAP_LIST:
			fprintf (stdout, "Client claims support of the following capabilities:\n");
			nc_cpblts_iter_start (client_supported_cpblts);
			while ((uri = nc_cpblts_iter_next (client_supported_cpblts)) != NULL) {
				fprintf (stdout, "%s\n", uri);
			}
			break;
		case CAP_DEF:
			if (client_supported_cpblts != NULL) {
				nc_cpblts_free (client_supported_cpblts);
			}
			client_supported_cpblts = nc_session_get_cpblts_default ();
			store_config (client_supported_cpblts);
			break;
		default:
			cmd_capability_help();
			break;
		}
	} while ((c=getopt_long (cmd.count, cmd.list, "a:r:l:d:h", long_options, &option_index)) != -1);

	clear_arglist(&cmd);

	return EXIT_SUCCESS;
}

void cmd_getconfig_help ()
{
	char *defaults;

	/* if session not established, print complete help for all capabilities */
	if (session == NULL || nc_cpblts_enabled (session, NC_CAP_WITHDEFAULTS_ID)) {
		defaults = "[--defaults report-all|report-all-tagged|trim|explicit] ";
	} else {
		defaults = "";
	}
	fprintf (stdout, "get-config [--help] %s[--filter [file]] running", defaults);
	if (session == NULL || nc_cpblts_enabled (session, NC_CAP_STARTUP_ID)) {
		fprintf (stdout, "|startup");
	}
	if (session == NULL || nc_cpblts_enabled (session, NC_CAP_CANDIDATE_ID)) {
		fprintf (stdout, "|candidate");
	}
	fprintf (stdout, "\n");
}

int cmd_getconfig (const char *arg)
{
	int c;
	NC_DATASTORE target;
	NCWD_MODE wd = NCWD_MODE_NOTSET;
	struct nc_filter *filter = NULL;
	nc_rpc *rpc = NULL;
	struct arglist cmd;
	struct option long_options[] ={
			{"defaults", 1, 0, 'd'},
			{"filter", 2, 0, 'f'},
			{"help", 0, 0, 'h'},
			{0, 0, 0, 0}
	};
	int option_index = 0;

	/* set back to start to be able to use getopt() repeatedly */
	optind = 0;

	if (session == NULL) {
		ERROR("get-config", "NETCONF session not established, use the \'connect\' command.");
		return (EXIT_FAILURE);
	}

	init_arglist (&cmd);
	addargs (&cmd, "%s", arg);

	while ((c = getopt_long (cmd.count, cmd.list, "d:f::h", long_options, &option_index)) != -1) {
		switch (c) {
		case 'd':
			wd = get_withdefaults("get-config", optarg);
			break;
		case 'f':
			filter = set_filter("get-config", optarg);
			if (filter == NULL) {
				clear_arglist(&cmd);
				return (EXIT_FAILURE);
			}
			break;
		case 'h':
			cmd_getconfig_help ();
			clear_arglist(&cmd);
			return (EXIT_SUCCESS);
			break;
		default:
			ERROR("get-config", "unknown option -%c.", c);
			cmd_getconfig_help ();
			clear_arglist(&cmd);
			return (EXIT_FAILURE);
		}
	}

	target = get_datastore("target", "get-config", &cmd, optind, NULL);

	/* arglist is no more needed */
	clear_arglist(&cmd);

	if (target == NC_DATASTORE_ERROR) {
		return (EXIT_FAILURE);
	}

	/* create requests */
	rpc = nc_rpc_getconfig (target, filter);
	nc_filter_free(filter);
	if (rpc == NULL) {
		ERROR("get-config", "creating an rpc request failed.");;
		return (EXIT_FAILURE);
	}
	/* set with defaults settings */
	if (nc_rpc_capability_attr(rpc, NC_CAP_ATTR_WITHDEFAULTS_MODE, wd) != EXIT_SUCCESS) {
		ERROR("get-config", "setting up the with-defaults mode failed.");
		nc_rpc_free(rpc);
		return (EXIT_FAILURE);
	}

	/* send the request and get the reply */
	return (send_recv_process("get-config", rpc));
}

void cmd_getschema_help ()
{
	/* if session not established, print complete help for all capabilities */
	fprintf (stdout, "get-schema [--help] [--version <version>] [--format <format>] <identifier>\n");
}

int cmd_getschema (const char *arg)
{
	int c;
	char *format = NULL, *version = NULL, *identifier = NULL;
	nc_rpc *rpc = NULL;
	struct arglist cmd;
	struct option long_options[] ={
			{"format", 1, 0, 'f'},
			{"version", 1, 0, 'v'},
			{"help", 0, 0, 'h'},
			{0, 0, 0, 0}
	};
	int option_index = 0;

	/* set back to start to be able to use getopt() repeatedly */
	optind = 0;

	if (session == NULL) {
		ERROR("get-config", "NETCONF session not established, use the \'connect\' command.");
		return (EXIT_FAILURE);
	}

	init_arglist (&cmd);
	addargs (&cmd, "%s", arg);

	while ((c = getopt_long (cmd.count, cmd.list, "f:hv:", long_options, &option_index)) != -1) {
		switch (c) {
		case 'f':
			format = optarg;
			break;
		case 'v':
			version = optarg;
			break;
		case 'h':
			cmd_getschema_help ();
			clear_arglist(&cmd);
			return (EXIT_SUCCESS);
			break;
		default:
			ERROR("get-config", "unknown option -%c.", c);
			cmd_getschema_help ();
			clear_arglist(&cmd);
			return (EXIT_FAILURE);
		}
	}

	if (optind == cmd.count) {
		/* force input from user */
		identifier = malloc (sizeof(char) * BUFFER_SIZE);
		if (identifier == NULL) {
			ERROR("get-schema", "memory allocation error (%s).", strerror (errno));
			clear_arglist(&cmd);
			return (EXIT_FAILURE);
		}

		INSTRUCTION("Set identifier of the schema to retrieve: ");
		if (scanf ("%1023s", identifier) == EOF) {
			ERROR("get-schema", "Reading the user input failed (%s).", (errno != 0) ? strerror(errno) : "Unexpected input");
			clear_arglist(&cmd);
			return (EXIT_FAILURE);
		}
	} else if ((optind + 1) == cmd.count) {
		identifier = strdup(cmd.list[optind]);
	} else {
		ERROR("get-schema", "invalid parameters, see \'get-schema --help\'.");
		clear_arglist(&cmd);
		return (EXIT_FAILURE);
	}

	/* create requests */
	rpc = nc_rpc_getschema(identifier, version, format);

	/* arglist is no more needed */
	clear_arglist(&cmd);
	free(identifier);

	if (rpc == NULL) {
		ERROR("get-schema", "creating an rpc request failed.");
		return (EXIT_FAILURE);
	}

	/* send the request and get the reply */
	return (send_recv_process("get-schema", rpc));
}

void cmd_un_lock_help (char* operation)
{
	/* if session not established, print complete help for all capabilities */
	fprintf (stdout, "%s running", operation);
	if (session == NULL || nc_cpblts_enabled (session, NC_CAP_STARTUP_ID)) {
		fprintf (stdout, "|startup");
	}
	if (session == NULL || nc_cpblts_enabled (session, NC_CAP_CANDIDATE_ID)) {
		fprintf (stdout, "|candidate");
	}
	fprintf (stdout, "\n");
}

#define LOCK_OP 1
#define UNLOCK_OP 2
int cmd_un_lock (int op, const char *arg)
{
	int c;
	NC_DATASTORE target;
	nc_rpc *rpc = NULL;
	struct arglist cmd;
	struct option long_options[] ={
			{"help", 0, 0, 'h'},
			{0, 0, 0, 0}
	};
	int option_index = 0;
	char *operation;

	switch (op) {
	case LOCK_OP:
		operation = "lock";
		break;
	case UNLOCK_OP:
		operation = "unlock";
		break;
	default:
		ERROR("cmd_un_lock()", "Wrong use of an internal function (Invalid parameter)");
		return (EXIT_FAILURE);
	}

	/* set back to start to be able to use getopt() repeatedly */
	optind = 0;

	if (session == NULL) {
		ERROR(operation, "NETCONF session not established, use the \'connect\' command.");
		return (EXIT_FAILURE);
	}

	init_arglist (&cmd);
	addargs (&cmd, "%s", arg);

	while ((c = getopt_long (cmd.count, cmd.list, "h", long_options, &option_index)) != -1) {
		switch (c) {
		case 'h':
			cmd_un_lock_help (operation);
			clear_arglist(&cmd);
			return (EXIT_SUCCESS);
			break;
		default:
			ERROR(operation, "unknown option -%c.", c);
			cmd_un_lock_help (operation);
			clear_arglist(&cmd);
			return (EXIT_FAILURE);
		}
	}

	target = get_datastore("target", operation, &cmd, optind, NULL);

	/* arglist is no more needed */
	clear_arglist(&cmd);

	if (target == NC_DATASTORE_ERROR) {
		return (EXIT_FAILURE);
	}

	/* create requests */
	switch (op) {
	case LOCK_OP:
		rpc = nc_rpc_lock(target);
		break;
	case UNLOCK_OP:
		rpc = nc_rpc_unlock(target);
		break;
	}
	if (rpc == NULL) {
		ERROR(operation, "creating an rpc request failed.");
		return (EXIT_FAILURE);
	}

	/* send the request and get the reply */
	return (send_recv_process(operation, rpc));
}

int cmd_lock (const char *arg)
{
	return cmd_un_lock (LOCK_OP, arg);
}

int cmd_unlock (const char *arg)
{
	return cmd_un_lock (UNLOCK_OP, arg);
}

#ifdef ENABLE_TLS
int cp(const char* to, const char* from) {
	int fd_to, fd_from;
	struct stat st;
	ssize_t from_len;
	int saved_errno;

	fd_from = open(from, O_RDONLY);
	if (fd_from < 0) {
		return -1;
	}

	fd_to = open(to, O_WRONLY | O_CREAT | O_TRUNC, 0600);
	if (fd_to < 0) {
		goto out_error;
	}

	if (fstat(fd_from, &st) < 0) {
		goto out_error;
	}

	from_len = st.st_size;

	if (sendfile(fd_to, fd_from, NULL, from_len) < from_len) {
		goto out_error;
	}
	return 0;

out_error:
	saved_errno = errno;

	close(fd_from);
	if (fd_to >= 0)
		close(fd_to);

	errno = saved_errno;
	return -1;
}

void parse_cert(const char* name, const char* path) {
	int i, j, has_san, first_san;
	ASN1_OCTET_STRING *ip;
	ASN1_INTEGER *bs;
	BIO *bio_out;
	FILE *fp;
	X509 *cert;
	STACK_OF(GENERAL_NAME) *san_names = NULL;
	GENERAL_NAME *san_name;

	fp = fopen(path, "r");
	if (fp == NULL) {
		ERROR("parse_cert", "Unable to open: %s", path);
		return;
	}
	cert = PEM_read_X509(fp, NULL, NULL, NULL);
	if (cert == NULL) {
		ERROR("parse_cert", "Unable to parse certificate: %s", path);
		fclose(fp);
		return;
	}

	bio_out = BIO_new_fp(stdout, BIO_NOCLOSE);

	bs = X509_get_serialNumber(cert);
	BIO_printf(bio_out, "-----%s----- serial: ", name);
	for (i = 0; i < bs->length; i++) {
		BIO_printf(bio_out, "%02x", bs->data[i]);
	}
	BIO_printf(bio_out, "\n");

	BIO_printf(bio_out, "Subject: ");
	X509_NAME_print(bio_out, X509_get_subject_name(cert), 0);
	BIO_printf(bio_out, "\n");

	BIO_printf(bio_out, "Issuer:  ");
	X509_NAME_print(bio_out, X509_get_issuer_name(cert), 0);
	BIO_printf(bio_out, "\n");

	BIO_printf(bio_out, "Valid until: ");
	ASN1_TIME_print(bio_out, X509_get_notAfter(cert));
	BIO_printf(bio_out, "\n");

	has_san = 0;
	first_san = 1;
	san_names = X509_get_ext_d2i(cert, NID_subject_alt_name, NULL, NULL);
	if (san_names != NULL) {
		for (i = 0; i < sk_GENERAL_NAME_num(san_names); ++i) {
			san_name = sk_GENERAL_NAME_value(san_names, i);
			if (san_name->type == GEN_EMAIL || san_name->type == GEN_DNS || san_name->type == GEN_IPADD) {
				if (!has_san) {
					BIO_printf(bio_out, "X509v3 Subject Alternative Name:\n\t");
					has_san = 1;
				}
				if (!first_san) {
					BIO_printf(bio_out, ", ");
				}
				if (first_san) {
					first_san = 0;
				}
				if (san_name->type == GEN_EMAIL) {
					BIO_printf(bio_out, "RFC822:%s", (char*) ASN1_STRING_data(san_name->d.rfc822Name));
				}
				if (san_name->type == GEN_DNS) {
					BIO_printf(bio_out, "DNS:%s", (char*) ASN1_STRING_data(san_name->d.dNSName));
				}
				if (san_name->type == GEN_IPADD) {
					BIO_printf(bio_out, "IP:");
					ip = san_name->d.iPAddress;
					if (ip->length == 4) {
						BIO_printf(bio_out, "%d.%d.%d.%d", ip->data[0], ip->data[1], ip->data[2], ip->data[3]);
					} else if (ip->length == 16) {
						for (j = 0; j < ip->length; ++j) {
							if (j > 0 && j < 15 && j%2 == 1) {
								BIO_printf(bio_out, "%02x:", ip->data[j]);
							} else {
								BIO_printf(bio_out, "%02x", ip->data[j]);
							}
						}
					}
				}
			}
		}
		sk_GENERAL_NAME_pop_free(san_names, GENERAL_NAME_free);
	}
	if (has_san) {
		BIO_printf(bio_out, "\n");
	}
	BIO_printf(bio_out, "\n");

	X509_free(cert);
	BIO_vfree(bio_out);
	fclose(fp);
}

void cmd_cert_help ()
{
	fprintf (stdout, "cert [--help | display | add <cert_path> | remove <cert_name> | displayown | replaceown (<cert_path.pem> | <cert_path.crt> <key_path.key>)]\n");
}

int cmd_cert (const char* arg)
{
	int ret;
	char* args = strdupa(arg);
	char* cmd = NULL, *ptr = NULL, *path, *path2, *dest;
	char* trusted_dir, *netconf_dir, *c_rehash_cmd;
	DIR* dir = NULL;
	struct dirent *d;

	cmd = strtok_r(args, " ", &ptr);
	cmd = strtok_r(NULL, " ", &ptr);
	if (cmd == NULL || strcmp(cmd, "--help") == 0 || strcmp(cmd, "-h") == 0) {
		cmd_cert_help();

	} else if (strcmp(cmd, "display") == 0) {
		int none = 1;
		char* name;

		if ((trusted_dir = get_default_trustedCA_dir(NULL)) == NULL) {
			ERROR("cert display", "Could not get the default trusted CA directory");
			return (EXIT_FAILURE);
		}

		dir = opendir(trusted_dir);
		while ((d = readdir(dir)) != NULL) {
			if (strcmp(d->d_name+strlen(d->d_name)-4, ".pem") == 0) {
				none = 0;
				name = strdup(d->d_name);
				name[strlen(name)-4] = '\0';
				asprintf(&path, "%s/%s", trusted_dir, d->d_name);
				parse_cert(name, path);
				free(name);
				free(path);
			}
		}
		closedir(dir);
		if (none) {
			fprintf(stdout, "No certificates found in the default trusted CA directory.\n");
		}
		free(trusted_dir);

	} else if (strcmp(cmd, "add") == 0) {
		path = strtok_r(NULL, " ", &ptr);
		if (path == NULL || strlen(path) < 5) {
			ERROR("cert add", "Missing or wrong path to the certificate");
			return (EXIT_FAILURE);
		}
		if (eaccess(path, R_OK) != 0) {
			ERROR("cert add", "Cannot access certificate \"%s\": %s", path, strerror(errno));
			return (EXIT_FAILURE);
		}

		trusted_dir = get_default_trustedCA_dir(NULL);
		if (trusted_dir == NULL) {
			ERROR("cert add", "Could not get the default trusted CA directory");
			return (EXIT_FAILURE);
		}

		if (asprintf(&dest, "%s/%s", trusted_dir, strrchr(path, '/')+1) == -1 || asprintf(&c_rehash_cmd, "c_rehash %s &> /dev/null", trusted_dir) == -1) {
			ERROR("cert add", "Memory allocation failed");
			free(trusted_dir);
			return (EXIT_FAILURE);
		}
		free(trusted_dir);

		if (strcmp(dest+strlen(dest)-4, ".pem") != 0) {
			ERROR("cert add", "CA certificates are expected to be in *.pem format");
			strcpy(dest+strlen(dest)-4, ".pem");
		}

		if (cp(dest, path) != 0) {
			ERROR("cert add", "Could not copy the certificate: %s", strerror(errno));
			free(dest);
			free(c_rehash_cmd);
			return (EXIT_FAILURE);
		}
		free(dest);

		if ((ret = system(c_rehash_cmd)) == -1 || WEXITSTATUS(ret) != 0) {
			ERROR("cert add", "c_rehash execution failed");
			free(c_rehash_cmd);
			return (EXIT_FAILURE);
		}

		free(c_rehash_cmd);

	} else if (strcmp(cmd, "remove") == 0) {
		path = strtok_r(NULL, " ", &ptr);
		if (path == NULL) {
			ERROR("cert remove", "Missing the certificate name");
			return (EXIT_FAILURE);
		}

		// delete ".pem" if the user unnecessarily included it
		if (strlen(path) > 4 && strcmp(path+strlen(path)-4, ".pem") == 0) {
			path[strlen(path)-4] = '\0';
		}

		trusted_dir = get_default_trustedCA_dir(NULL);
		if (trusted_dir == NULL) {
			ERROR("cert remove", "Could not get the default trusted CA directory");
			return (EXIT_FAILURE);
		}

		if (asprintf(&dest, "%s/%s.pem", trusted_dir, path) == -1 || asprintf(&c_rehash_cmd, "c_rehash %s &> /dev/null", trusted_dir) == -1) {
			ERROR("cert remove", "Memory allocation failed");
			free(trusted_dir);
			return (EXIT_FAILURE);
		}
		free(trusted_dir);

		if (remove(dest) != 0) {
			ERROR("cert remove", "Cannot remove certificate \"%s\": %s (use the name from \"cert display\" output)", path, strerror(errno));
			free(dest);
			free(c_rehash_cmd);
			return (EXIT_FAILURE);
		}
		free(dest);

		if ((ret = system(c_rehash_cmd)) == -1 || WEXITSTATUS(ret) != 0) {
			ERROR("cert remove", "c_rehash execution failed");
			free(c_rehash_cmd);
			return (EXIT_FAILURE);
		}

		free(c_rehash_cmd);

	} else if (strcmp(cmd, "displayown") == 0) {
		int crt = 0, key = 0, pem = 0;

		netconf_dir = get_netconf_dir();
		if (netconf_dir == NULL) {
			ERROR("cert displayown", "Could not get the client home directory");
			return (EXIT_FAILURE);
		}

		if (asprintf(&dest, "%s/client.pem", netconf_dir) == -1) {
			ERROR("cert displayown", "Memory allocation failed");
			free(netconf_dir);
			return (EXIT_FAILURE);
		}
		free(netconf_dir);
		if (eaccess(dest, R_OK) == 0) {
			pem = 1;
		}

		strcpy(dest+strlen(dest)-4, ".key");
		if (eaccess(dest, R_OK) == 0) {
			key = 1;
		}

		strcpy(dest+strlen(dest)-4, ".crt");
		if (eaccess(dest, R_OK) == 0) {
			crt = 1;
		}

		if (!crt && !key && !pem) {
			fprintf(stdout, "FAIL: No client certificate found, use \"cert replaceown\" to set some.\n");
		} else if (crt && !key && !pem) {
			fprintf(stdout, "FAIL: Client *.crt certificate found, but is of no use without its private key *.key.\n");
		} else if (!crt && key && !pem) {
			fprintf(stdout, "FAIL: Private key *.key found, but is of no use without a certificate.\n");
		} else if (!crt && !key && pem) {
			fprintf(stdout, "OK: Using *.pem client certificate with the included private key.\n");
		} else if (crt && key && !pem) {
			fprintf(stdout, "OK: Using *.crt certificate with a separate private key.\n");
		} else if (crt && !key && pem) {
			fprintf(stdout, "WORKING: Using *.pem client certificate with the included private key (leftover certificate *.crt detected).\n");
		} else if (!crt && key && pem) {
			fprintf(stdout, "WORKING: Using *.pem client certificate with the included private key (leftover private key detected).\n");
		} else if (crt && key && pem) {
			fprintf(stdout, "WORKING: Using *.crt certificate with a separate private key (lower-priority *.pem certificate with a private key detected).\n");
		}

		if (crt) {
			parse_cert("CRT", dest);
		}
		if (pem) {
			strcpy(dest+strlen(dest)-4, ".pem");
			parse_cert("PEM", dest);
		}
		free(dest);

	} else if (strcmp(cmd, "replaceown") == 0) {
		path = strtok_r(NULL, " ", &ptr);
		if (path == NULL || strlen(path) < 5) {
			ERROR("cert replaceown", "Missing the certificate or invalid path.");
			return (EXIT_FAILURE);
		}
		if (eaccess(path, R_OK) != 0) {
			ERROR("cert replaceown", "Cannot access the certificate \"%s\": %s", path, strerror(errno));
			return (EXIT_FAILURE);
		}

		path2 = strtok_r(NULL, " ", &ptr);
		if (path2 != NULL) {
			if (strlen(path2) < 5) {
				ERROR("cert replaceown", "Invalid private key path.");
				return (EXIT_FAILURE);
			}
			if (eaccess(path2, R_OK) != 0) {
				ERROR("cert replaceown", "Cannot access the private key \"%s\": %s", path2, strerror(errno));
				return (EXIT_FAILURE);
			}
		}

		netconf_dir = get_netconf_dir();
		if (netconf_dir == NULL) {
			ERROR("cert replaceown", "Could not get the client home directory");
			return (EXIT_FAILURE);
		}
		if (asprintf(&dest, "%s/client.XXX", netconf_dir) == -1) {
			ERROR("cert replaceown", "Memory allocation failed");
			free(netconf_dir);
			return (EXIT_FAILURE);
		}
		free(netconf_dir);

		if (path2 != NULL) {
			/* CRT & KEY */
			strcpy(dest+strlen(dest)-4, ".pem");
			if (remove(dest) != 0 && errno == EACCES) {
				ERROR("cert replaceown", "Could not remove old certificate (*.pem)");
			}

			strcpy(dest+strlen(dest)-4, ".crt");
			if (cp(dest, path) != 0) {
				ERROR("cert replaceown", "Could not copy the certificate \"%s\": %s", path, strerror(errno));
				free(dest);
				return (EXIT_FAILURE);
			}
			strcpy(dest+strlen(dest)-4, ".key");
			if (cp(dest, path2) != 0) {
				ERROR("cert replaceown", "Could not copy the private key \"%s\": %s", path, strerror(errno));
				free(dest);
				return (EXIT_FAILURE);
			}
		} else {
			/* PEM */
			strcpy(dest+strlen(dest)-4, ".key");
			if (remove(dest) != 0 && errno == EACCES) {
				ERROR("cert replaceown", "Could not remove old private key");
			}
			strcpy(dest+strlen(dest)-4, ".crt");
			if (remove(dest) != 0 && errno == EACCES) {
				ERROR("cert replaceown", "Could not remove old certificate (*.crt)");
			}

			strcpy(dest+strlen(dest)-4, ".pem");
			if (cp(dest, path) != 0) {
				ERROR("cert replaceown", "Could not copy the certificate \"%s\": %s", path, strerror(errno));
				free(dest);
				return (EXIT_FAILURE);
			}
		}

		free(dest);

	} else {
		ERROR("cert", "Unknown argument %s", cmd);
		return (EXIT_FAILURE);
	}

	return (EXIT_SUCCESS);
}

void parse_crl(const char* name, const char* path) {
	int i;
	BIO *bio_out;
	FILE *fp;
	X509_CRL *crl;
	ASN1_INTEGER* bs;
	X509_REVOKED* rev;

	fp = fopen(path, "r");
	if (fp == NULL) {
		ERROR("parse_crl", "Unable to open \"%s\": %s", path, strerror(errno));
		return;
	}
	crl = PEM_read_X509_CRL(fp, NULL, NULL, NULL);
	if (crl == NULL) {
		ERROR("parse_crl", "Unable to parse certificate: %s", path);
		fclose(fp);
		return;
	}

	bio_out = BIO_new_fp(stdout, BIO_NOCLOSE);

	BIO_printf(bio_out, "-----%s-----\n", name);

	BIO_printf(bio_out, "Issuer: ");
	X509_NAME_print(bio_out, X509_CRL_get_issuer(crl), 0);
	BIO_printf(bio_out, "\n");

	BIO_printf(bio_out, "Last update: ");
	ASN1_TIME_print(bio_out, X509_CRL_get_lastUpdate(crl));
	BIO_printf(bio_out, "\n");

	BIO_printf(bio_out, "Next update: ");
	ASN1_TIME_print(bio_out, X509_CRL_get_nextUpdate(crl));
	BIO_printf(bio_out, "\n");

	BIO_printf(bio_out, "REVOKED:\n");

	if ((rev = sk_X509_REVOKED_pop(X509_CRL_get_REVOKED(crl))) == NULL) {
		BIO_printf(bio_out, "\tNone\n");
	}
	while (rev != NULL) {
		bs = rev->serialNumber;
		BIO_printf(bio_out, "\tSerial no.: ");
		for (i = 0; i < bs->length; i++) {
			BIO_printf(bio_out, "%02x", bs->data[i]);
		}
		BIO_printf(bio_out, "  Date: ");

		ASN1_TIME_print(bio_out, rev->revocationDate);
		BIO_printf(bio_out, "\n");

		X509_REVOKED_free(rev);
		rev = sk_X509_REVOKED_pop(X509_CRL_get_REVOKED(crl));
	}

	X509_CRL_free(crl);
	BIO_vfree(bio_out);
	fclose(fp);
}

void cmd_crl_help() {
	fprintf (stdout, "crl [--help | display | add <crl_path> | remove <crl_name>]\n");
}

int cmd_crl (const char* arg) {
	int ret;
	char* args = strdupa(arg);
	char* cmd = NULL, *ptr = NULL, *path, *dest;
	char* crl_dir, *c_rehash_cmd;
	DIR* dir = NULL;
	struct dirent *d;

	cmd = strtok_r(args, " ", &ptr);
	cmd = strtok_r(NULL, " ", &ptr);
	if (cmd == NULL || strcmp(cmd, "--help") == 0 || strcmp(cmd, "-h") == 0) {
		cmd_crl_help();

	} else if (strcmp(cmd, "display") == 0) {
		int none = 1;
		char* name;

		if ((crl_dir = get_default_CRL_dir(NULL)) == NULL) {
			ERROR("crl display", "Could not get the default CRL directory");
			return (EXIT_FAILURE);
		}

		dir = opendir(crl_dir);
		while ((d = readdir(dir)) != NULL) {
			if (strcmp(d->d_name+strlen(d->d_name)-4, ".pem") == 0) {
				none = 0;
				name = strdup(d->d_name);
				name[strlen(name)-4] = '\0';
				asprintf(&path, "%s/%s", crl_dir, d->d_name);
				parse_crl(name, path);
				free(name);
				free(path);
			}
		}
		closedir(dir);
		if (none) {
			fprintf(stdout, "No CRLs found in the default CRL directory.\n");
		}
		free(crl_dir);

	} else if (strcmp(cmd, "add") == 0) {
		path = strtok_r(NULL, " ", &ptr);
		if (path == NULL || strlen(path) < 5) {
			ERROR("crl add", "Missing or wrong path to the certificate");
			return (EXIT_FAILURE);
		}
		if (eaccess(path, R_OK) != 0) {
			ERROR("crl add", "Cannot access certificate \"%s\": %s", path, strerror(errno));
			return (EXIT_FAILURE);
		}

		crl_dir = get_default_CRL_dir(NULL);
		if (crl_dir == NULL) {
			ERROR("crl add", "Could not get the default CRL directory");
			return (EXIT_FAILURE);
		}

		if (asprintf(&dest, "%s/%s", crl_dir, strrchr(path, '/')+1) == -1 || asprintf(&c_rehash_cmd, "c_rehash %s &> /dev/null", crl_dir) == -1) {
			ERROR("crl add", "Memory allocation failed");
			free(crl_dir);
			return (EXIT_FAILURE);
		}
		free(crl_dir);

		if (strcmp(dest+strlen(dest)-4, ".pem") != 0) {
			ERROR("crl add", "CRLs are expected to be in *.pem format");
			strcpy(dest+strlen(dest)-4, ".pem");
		}

		if (cp(dest, path) != 0) {
			ERROR("crl add", "Could not copy the CRL \"%s\": %s", path, strerror(errno));
			free(dest);
			free(c_rehash_cmd);
			return (EXIT_FAILURE);
		}
		free(dest);

		if ((ret = system(c_rehash_cmd)) == -1 || WEXITSTATUS(ret) != 0) {
			ERROR("crl add", "c_rehash execution failed");
			free(c_rehash_cmd);
			return (EXIT_FAILURE);
		}

		free(c_rehash_cmd);

	} else if (strcmp(cmd, "remove") == 0) {
		path = strtok_r(NULL, " ", &ptr);
		if (path == NULL) {
			ERROR("crl remove", "Missing the certificate name");
			return (EXIT_FAILURE);
		}

		// delete ".pem" if the user unnecessarily included it
		if (strlen(path) > 4 && strcmp(path+strlen(path)-4, ".pem") == 0) {
			path[strlen(path)-4] = '\0';
		}

		crl_dir = get_default_CRL_dir(NULL);
		if (crl_dir == NULL) {
			ERROR("crl remove", "Could not get the default CRL directory");
			return (EXIT_FAILURE);
		}

		if (asprintf(&dest, "%s/%s.pem", crl_dir, path) == -1 || asprintf(&c_rehash_cmd, "c_rehash %s &> /dev/null", crl_dir) == -1) {
			ERROR("crl remove", "Memory allocation failed");
			free(crl_dir);
			return (EXIT_FAILURE);
		}
		free(crl_dir);

		if (remove(dest) != 0) {
			ERROR("crl remove", "Cannot remove CRL \"%s\": %s (use the name from \"crl display\" output)", path, strerror(errno));
			free(dest);
			free(c_rehash_cmd);
			return (EXIT_FAILURE);
		}
		free(dest);

		if ((ret = system(c_rehash_cmd)) == -1 || WEXITSTATUS(ret) != 0) {
			ERROR("crl remove", "c_rehash execution failed");
			free(c_rehash_cmd);
			return (EXIT_FAILURE);
		}

		free(c_rehash_cmd);

	} else {
		ERROR("crl", "Unknown argument %s", cmd);
		return (EXIT_FAILURE);
	}

	return (EXIT_SUCCESS);
}
#endif

void cmd_connect_help ()
{
#ifdef ENABLE_TLS
	fprintf (stdout, "connect [--help] [--port <num>] [--login <username>] [--tls] [--cert <cert_path> [--key <key_path>]] [--trusted <trusted_CA_store.pem>] host\n");
#else
	fprintf (stdout, "connect [--help] [--port <num>] [--login <username>] host\n");
#endif
}

void cmd_listen_help ()
{
#ifdef ENABLE_TLS
	fprintf (stdout, "listen [--help] [--port <num>] [--login <username>] [--tls] [--cert <cert_path> [--key <key_path>]] [--trusted <trusted_CA_store.pem>]\n");
#else
	fprintf (stdout, "listen [--help] [--port <num>] [--login <username>]\n");
#endif
}

#define DEFAULT_PORT_SSH 830
#define DEFAULT_PORT_TLS 6513

#define DEFAULT_PORT_CH_SSH 6666
#define DEFAULT_PORT_CH_TLS 6667
#define ACCEPT_TIMEOUT 3600000 /* 1 minute */

static int cmd_connect_listen (const char* arg, int is_connect)
{
	char* func_name = (is_connect ? strdupa("connect") : strdupa("listen"));
#ifndef DISABLE_CALLHOME
	static unsigned short listening = 0;
	int timeout = ACCEPT_TIMEOUT;
#endif
	char *host = NULL, *user = NULL;
#ifdef ENABLE_TLS
	DIR* dir = NULL;
	struct dirent* d;
	int usetls = 0, n;
	char *cert = NULL, *key = NULL, *trusted_dir = NULL, *crl_dir = NULL, *trusted_store = NULL;
#endif
	int hostfree = 0;
	unsigned short port = 0;
	int c;
	struct arglist cmd;
	struct option long_options[] = {
			{"help", 0, 0, 'h'},
			{"port", 1, 0, 'p'},
			{"login", 1, 0, 'l'},
#ifdef ENABLE_TLS
			{"tls", 0, 0, 't'},
			{"cert", 1, 0, 'c'},
			{"key", 1, 0, 'k'},
			{"trusted", 1, 0, 's'},
#endif
			{0, 0, 0, 0}
	};
	int option_index = 0;

	/* set back to start to be able to use getopt() repeatedly */
	optind = 0;

	if (session != NULL) {
		ERROR(func_name, "already connected to %s.", nc_session_get_host (session));
		return (EXIT_FAILURE);
	}

	/* set default transport protocol */
	nc_session_transport(NC_TRANSPORT_SSH);

	/* process given arguments */
	init_arglist (&cmd);
	addargs (&cmd, "%s", arg);

#ifdef ENABLE_TLS
	while ((c = getopt_long (cmd.count, cmd.list, "hp:l:tc:k:s:", long_options, &option_index)) != -1)
#else
	while ((c = getopt_long (cmd.count, cmd.list, "hp:l:", long_options, &option_index)) != -1)
#endif
	{
		switch (c) {
		case 'h':
			if (is_connect) {
				cmd_connect_help();
			} else {
				cmd_listen_help();
			}
			clear_arglist(&cmd);
			return (EXIT_SUCCESS);
			break;
		case 'p':
			port = (unsigned short) atoi (optarg);
#ifndef DISABLE_CALLHOME
			if (!is_connect && listening != 0 && listening != port) {
				nc_callhome_listen_stop();
				listening = 0;
			}
#endif
			break;
		case 'l':
			user = optarg;
			break;
#ifdef ENABLE_TLS
		case 't':
			if (nc_session_transport(NC_TRANSPORT_TLS) == EXIT_SUCCESS) {
				if (port == 0) {
					port = (is_connect ? DEFAULT_PORT_TLS : DEFAULT_PORT_CH_TLS);
				}
				usetls = 1;
			}
			break;
		case 'c':
			asprintf(&cert, "%s", optarg);
			break;
		case 'k':
			asprintf(&key, "%s", optarg);
			break;
		case 's':
			trusted_store = optarg;
			break;
#endif
		default:
			ERROR(func_name, "unknown option -%c.", c);
			if (is_connect) {
				cmd_connect_help();
			} else {
				cmd_listen_help();
			}
			goto error_cleanup;
		}
	}
	if (port == 0) {
		port = (is_connect ? DEFAULT_PORT_SSH : DEFAULT_PORT_CH_SSH);
	}
#ifdef ENABLE_TLS
	if (usetls) {
		/* use the default TLS user if not specified by user
		 * (it does not have any effect except for seeing it
		 * in status command as the session user) */
		if (user == NULL) {
			user = strdupa("certificate-based");
		}

		if (cert == NULL) {
			if (key != NULL) {
				ERROR(func_name, "Key specified without a certificate.");
				goto error_cleanup;
			}
			get_default_client_cert(&cert, &key);
			if (cert == NULL) {
				ERROR(func_name, "Could not find the default client certificate, check with \"cert displayown\" command.");
				goto error_cleanup;
			}
		}
		if (trusted_store == NULL) {
			trusted_dir = get_default_trustedCA_dir(NULL);
			if ((dir = opendir(trusted_dir)) == NULL) {
				ERROR(func_name, "Could not use the trusted CA directory.");
				goto error_cleanup;
			}

			/* check whether we have any trusted CA, verification should fail otherwise */
			n = 0;
			while ((d = readdir(dir)) != NULL) {
				if (++n > 2) {
					break;
				}
			}
			closedir(dir);
			if (n <= 2) {
				ERROR(func_name, "Trusted CA directory empty, use \"cert add\" command to add certificates.");
			}
		} else {
			if (eaccess(trusted_store, R_OK) != 0) {
				ERROR(func_name, "Could not access trusted CA store \"%s\": %s", trusted_store, strerror(errno));
				goto error_cleanup;
			}
			if (strlen(trusted_store) < 5 || strcmp(trusted_store+strlen(trusted_store)-4, ".pem") != 0) {
				ERROR(func_name, "Trusted CA store in an unknown format.");
				goto error_cleanup;
			}
		}
		if ((crl_dir = get_default_CRL_dir(NULL)) == NULL) {
			ERROR(func_name, "Could not use the CRL directory.");
			goto error_cleanup;
		}

		if (nc_tls_init(cert, key, trusted_store, trusted_dir, NULL, crl_dir) != EXIT_SUCCESS) {
			ERROR(func_name, "Initiating TLS failed.");
			goto error_cleanup;
		}
	}
#endif

	if (is_connect) {
		if (optind == cmd.count) {
			/* get mandatory argument */
			host = malloc (sizeof(char) * BUFFER_SIZE);
			if (host == NULL) {
				ERROR(func_name, "memory allocation error (%s).", strerror (errno));
				goto error_cleanup;
			}
			hostfree = 1;
			INSTRUCTION("Hostname to connect to: ");
			if (scanf ("%1023s", host) == EOF) {
				ERROR(func_name, "Reading the user input failed (%s).", (errno != 0) ? strerror(errno) : "Unexpected input");
				goto error_cleanup;
			}
		} else if ((optind + 1) == cmd.count) {
			host = cmd.list[optind];
		}

		/* create the session */
		session = nc_session_connect (host, port, user, client_supported_cpblts);
		if (session == NULL) {
			ERROR(func_name, "connecting to the %s:%d as user \"%s\" failed.", host, port, user);
			if (hostfree) {
				free (host);
			}
			goto error_cleanup;
		}
		if (hostfree) {
			free (host);
		}
	} else {
#ifndef DISABLE_CALLHOME
		/* create the session */
		if (!listening) {
			if (nc_callhome_listen(port) == EXIT_FAILURE) {
				ERROR(func_name, "unable to start listening for incoming Call Home");
				goto error_cleanup;
			}
			listening = port;
		}

		if (verb_level == 0) {
			fprintf(stdout, "\tWaiting 60 minute for call home on port %d...\n", port);
		}
		session = nc_callhome_accept(user, client_supported_cpblts, &timeout);
		if (session == NULL ) {
			if (timeout == 0) {
				ERROR(func_name, "no call home")
			} else {
				ERROR(func_name, "receiving call Home failed.");
			}
		}
#endif
	}

#ifdef ENABLE_TLS
	if (trusted_dir != NULL) {
		free(trusted_dir);
	}
	if (crl_dir != NULL) {
		free(crl_dir);
	}
	if (cert != NULL) {
		free(cert);
	}
	if (key != NULL) {
		free(key);
	}
#endif
	clear_arglist(&cmd);

	return (EXIT_SUCCESS);

error_cleanup:
#ifdef ENABLE_TLS
	if (trusted_dir != NULL) {
		free(trusted_dir);
	}
	if (crl_dir != NULL) {
		free(crl_dir);
	}
	if (cert != NULL) {
		free(cert);
	}
	if (key != NULL) {
		free(key);
	}
#endif
	clear_arglist(&cmd);
	return (EXIT_FAILURE);
}

int cmd_connect (const char* arg)
{
	return cmd_connect_listen(arg, 1);
}

#ifndef DISABLE_CALLHOME
int cmd_listen (const char* arg)
{
	return cmd_connect_listen(arg, 0);
}
#endif

int cmd_disconnect (const char* UNUSED(arg))
{
	if (session == NULL) {
		ERROR("disconnect", "not connected to any NETCONF server.");
	} else {
		nc_session_free(session);
		session = NULL;
	}

	return (EXIT_SUCCESS);
}

int cmd_quit (const char* UNUSED(arg))
{
	done = 1;
	if (session != NULL) {
		cmd_disconnect (NULL);
	}
	return (0);
}

int cmd_verbose (const char *UNUSED(arg))
{
	if (verb_level != 1) {
		verb_level = 1;
		nc_verbosity (NC_VERB_VERBOSE);
		fprintf (stdout, "Verbose level set to VERBOSE\n");
	} else {
		verb_level = 0;
		nc_verbosity (NC_VERB_ERROR);
		fprintf (stdout, "Verbose messages switched off\n");
	}

	return (EXIT_SUCCESS);
}

int cmd_debug (const char *UNUSED(arg))
{
	if (verb_level != 2) {
		verb_level = 2;
		nc_verbosity (NC_VERB_DEBUG);
		fprintf (stdout, "Verbose level set to DEBUG\n");
	} else {
		verb_level = 0;
		nc_verbosity (NC_VERB_ERROR);
		fprintf (stdout, "Verbose messages switched off\n");
	}

	return (EXIT_SUCCESS);
}

int cmd_help (const char* arg)
{
	int i;
	char *args = strdupa(arg);
	char *cmd = NULL;
	char cmdline[BUFFER_SIZE];

	strtok (args, " ");
	if ((cmd = strtok (NULL, " ")) == NULL) {
		/* generic help for the application */
		print_version ();

generic_help:
		INSTRUCTION("Available commands:\n");

		for (i = 0; commands[i].name; i++) {
			if (commands[i].helpstring != NULL) {
				fprintf (stdout, "  %-15s %s\n", commands[i].name, commands[i].helpstring);
			}
		}
	} else {
		/* print specific help for the selected command */

		/* get the command of the specified name */
		for (i = 0; commands[i].name; i++) {
			if (strcmp(cmd, commands[i].name) == 0) {
				break;
			}
		}

		/* execute the command's help if any valid command specified */
		if (commands[i].name) {
			snprintf(cmdline, BUFFER_SIZE, "%s --help", commands[i].name);
			commands[i].func(cmdline);
		} else {
			/* if unknown command specified, print the list of commands */
			fprintf(stdout, "Unknown command \'%s\'\n", cmd);
			goto generic_help;
		}
	}

	return (0);
}

struct ntf_thread_config {
	struct nc_session *session;
	FILE* output;
};

#ifndef DISABLE_NOTIFICATIONS

static pthread_key_t ntf_file;
static volatile int ntf_file_flag = 0; /* flag if the thread specific key is already initiated */
static void notification_fileprint (time_t eventtime, const char* content)
{
	FILE *f;
	char t[128];

	t[0] = 0;
	if ((f = (FILE*) pthread_getspecific(ntf_file)) != NULL) {
		strftime(t, sizeof(t), "%c", localtime(&eventtime));
		fprintf(f, "eventTime: %s\n%s\n", t, content);
		fflush(f);
	}
}

void* notification_thread(void* arg)
{
	struct ntf_thread_config *config = (struct ntf_thread_config*)arg;

	pthread_setspecific(ntf_file, (void*)config->output);
	ncntf_dispatch_receive(config->session, notification_fileprint);
	if (config->output != stdout) {
		fclose(config->output);
	}
	free(config);

	return (NULL);
}

void cmd_subscribe_help()
{
	fprintf (stdout, "subscribe [--help] [--filter [file]] [--begin <time>] [--end <time>] [--output <file>] [<stream>]\n");
	fprintf (stdout, "\t<time> has following format:\n");
	fprintf (stdout, "\t\t+<num>  - current time plus the given number of seconds.\n");
	fprintf (stdout, "\t\t<num>   - absolute time as number of seconds since 1970-01-01.\n");
	fprintf (stdout, "\t\t-<num>  - current time minus the given number of seconds.\n");
}

int cmd_subscribe(const char *arg)
{
	int c;
	struct nc_filter *filter = NULL;
	char *stream;
	time_t t, start = -1, stop = -1;
	nc_rpc *rpc = NULL;
	FILE *output = NULL;
	struct arglist cmd;
	struct option long_options[] ={
			{"filter", 2, 0, 'f'},
			{"help", 0, 0, 'h'},
			{"begin", 1, 0, 'b'},
			{"end", 1, 0, 'e'},
			{"output", 1, 0, 'o'},
			{0, 0, 0, 0}
	};
	int option_index = 0;
	pthread_t thread;
	struct ntf_thread_config *tconfig;

	/* set back to start to be able to use getopt() repeatedly */
	optind = 0;

	if (session == NULL) {
		ERROR("subscribe", "NETCONF session not established, use the \'connect\' command.");
		return (EXIT_FAILURE);
	}

	/* check if notifications are allowed on this session */
	if (nc_session_notif_allowed(session) == 0) {
		ERROR("subscribe", "Notification subscription is not allowed on this session.");
		return (EXIT_FAILURE);
	}

	init_arglist (&cmd);
	addargs (&cmd, "%s", arg);

	while ((c = getopt_long (cmd.count, cmd.list, "bef::ho:", long_options, &option_index)) != -1) {
		switch (c) {
		case 'b':
		case 'e':
			if (optarg[0] == '-' || optarg[0] == '+') {
				if ((t = time(NULL)) == -1) {
					ERROR("subscribe", "Getting the current time failed (%s)", strerror(errno));
					return (EXIT_FAILURE);
				}
				t = t + strtol(optarg, NULL, 10);
			} else {
				t = strtol(optarg, NULL, 10);
			}

			if (c == 'b') {
				if (t > time(NULL)) {
					/* begin time is in future */
					ERROR("subscribe", "Begin time cannot be set to future.");
					clear_arglist(&cmd);
					return (EXIT_FAILURE);
				}
				start = t;
			} else { /* c == 'e' */
				stop = t;
			}
			break;
		case 'f':
			filter = set_filter("create-subscription", optarg);
			if (filter == NULL) {
				clear_arglist(&cmd);
				return (EXIT_FAILURE);
			}
			break;
		case 'h':
			cmd_subscribe_help ();
			clear_arglist(&cmd);
			return (EXIT_SUCCESS);
			break;
		case 'o':
			fprintf(stderr,"file: %s", optarg);
			output = fopen(optarg, "w");
			if (output == NULL) {
				ERROR("create-subscription", "opening the output file failed (%s).", strerror(errno));
				clear_arglist(&cmd);
				return (EXIT_FAILURE);
			}
			break;
		default:
			ERROR("create-subscription", "unknown option -%c.", c);
			cmd_subscribe_help ();
			clear_arglist(&cmd);
			return (EXIT_FAILURE);
		}
	}

	/* check times */
	if (start != -1 && stop != -1 && start > stop) {
		ERROR("subscribe", "Subscription start time must be lower than the end time.");
		return (EXIT_FAILURE);
	}

	if ((optind + 1) < cmd.count) {
		ERROR("create-subscription", "invalid parameters, see \'get --help\'.");
		clear_arglist(&cmd);
		return (EXIT_FAILURE);
	} else if ((optind + 1) == cmd.count) {
		/* stream specified */
		stream = cmd.list[optind];
	} else {
		stream = NULL;
	}


	/* create requests */
	rpc = nc_rpc_subscribe(stream, filter, (start == -1)?NULL:&start, (stop == -1)?NULL:&stop);
	nc_filter_free(filter);
	clear_arglist(&cmd);
	if (rpc == NULL) {
		ERROR("create-subscription", "creating an rpc request failed.");
		return (EXIT_FAILURE);
	}

	if (send_recv_process("subscribe", rpc) != 0) {
		return (EXIT_FAILURE);
	}
	rpc = NULL; /* just note that rpc is already freed by send_recv_process() */

	/*
	 * create Notifications receiving thread
	 */
	/* check thread specific variable */
	if (ntf_file_flag == 0) {
		ntf_file_flag = 1;
		pthread_key_create(&ntf_file, NULL);
	}

	tconfig = malloc(sizeof(struct ntf_thread_config));
	tconfig->session = session;
	tconfig->output = (output == NULL) ? stdout : output;
	if (pthread_create(&thread, NULL, notification_thread, tconfig) != 0) {
		ERROR("create-subscription", "creating a thread for receiving notifications failed");
		return (EXIT_FAILURE);
	}
	pthread_detach(thread);
	return (EXIT_SUCCESS);
}
#endif /* DISABLE_NOTIFICATIONS */

void cmd_userrpc_help()
{
	fprintf (stdout, "user-rpc [--help] [--file <file>]\n\n"
	"\'--file <file>\' - input file with RPC message content.\n"
	"If \'--file\' is omitted, user is asked to enter content manually.\n");
}

int cmd_userrpc(const char *arg)
{
	int c;
	int config_fd;
	struct stat config_stat;
	char *config = NULL, *config_m = NULL;
	nc_rpc *rpc = NULL;
	struct arglist cmd;
	struct option long_options[] ={
			{"file", 1, 0, 'f'},
			{"help", 0, 0, 'h'},
			{0, 0, 0, 0}
	};
	int option_index = 0;

	/* set back to start to be able to use getopt() repeatedly */
	optind = 0;

	if (session == NULL) {
		ERROR("user-rpc", "NETCONF session not established, use the \'connect\' command.");
		return (EXIT_FAILURE);
	}

	init_arglist (&cmd);
	addargs (&cmd, "%s", arg);

	while ((c = getopt_long (cmd.count, cmd.list, "f:h", long_options, &option_index)) != -1) {
		switch (c) {
		case 'f':
			/* open edit configuration data from the file */
			config_fd = open(optarg, O_RDONLY);
			if (config_fd == -1) {
				ERROR("user-rpc", "unable to open a local file (%s).", strerror(errno));
				clear_arglist(&cmd);
				return (EXIT_FAILURE);
			}

			/* map content of the file into the memory */
			fstat(config_fd, &config_stat);
			config_m = (char*) mmap(NULL, config_stat.st_size, PROT_READ, MAP_PRIVATE, config_fd, 0);
			if (config_m == MAP_FAILED) {
				ERROR("user-rpc", "mmapping of a local datastore file failed (%s).", strerror(errno));
				clear_arglist(&cmd);
				close(config_fd);
				return (EXIT_FAILURE);
			}

			/* make a copy of the content to allow closing the file */
			config = strdup(config_m);

			/* unmap local datastore file and close it */
			munmap(config_m, config_stat.st_size);
			close(config_fd);
			break;
		case 'h':
			cmd_userrpc_help ();
			clear_arglist(&cmd);
			return (EXIT_SUCCESS);
			break;
		default:
			ERROR("user-rpc", "unknown option -%c.", c);
			cmd_userrpc_help ();
			clear_arglist(&cmd);
			return (EXIT_FAILURE);
		}
	}

	/* arglist is no more needed */
	clear_arglist(&cmd);

	if (config == NULL) {
		INSTRUCTION("Type the content of a RPC operation (close editor by Ctrl-D):\n");
		config = mreadline(NULL);
		if (config == NULL) {
			ERROR("copy-config", "reading filter failed.");
			return (EXIT_FAILURE);
		}
	}

	/* create requests */
	rpc = nc_rpc_generic (config);
	free(config);
	if (rpc == NULL) {
		ERROR("user-rpc", "creating an rpc request failed.");
		return (EXIT_FAILURE);
	}

	/* send the request and get the reply */
	return (send_recv_process("user-rpc", rpc));
}

void cmd_discardchanges_help()
{
	fprintf (stdout, "discard-changes\n");
}

int cmd_discardchanges(const char *arg)
{
	return (cmd_generic_op(GO_DISCARD_CHANGES, arg));
}

void cmd_commit_help()
{
	fprintf (stdout, "commit\n");
}

int cmd_commit(const char *arg)
{
	return (cmd_generic_op(GO_COMMIT, arg));
}

int cmd_generic_op(GENERIC_OPS op, const char *arg)
{
	int i;
	char* args = strdupa(arg);
	char* op_string = NULL;
	nc_rpc* (*op_func)(void);
	void (*op_help)(void);
	nc_rpc *rpc = NULL;

	switch (op) {
	case GO_COMMIT:
		op_func = nc_rpc_commit;
		op_help = cmd_commit_help;
		op_string = "commit";
		break;
	case GO_DISCARD_CHANGES:
		op_func = nc_rpc_discardchanges;
		op_help = cmd_discardchanges_help;
		op_string = "discard-changes";
		break;
	default:
		ERROR(op_string, "Unknown generic operation.");
		return (EXIT_FAILURE);
	}

	/* check input parameters - no parameter is accepted */
	/* remove trailing white spaces */
	for (i = strlen(args) - 1; i >= 0 && isspace(args[i]); i--) {
		args[i] = '\0';
	}
	if (strcmp(args, op_string) != 0) {
		op_help();
		return (EXIT_FAILURE);
	}

	/* create requests */
	rpc = op_func();
	if (rpc == NULL) {
		ERROR(op_string, "creating an rpc request failed.");
		return (EXIT_FAILURE);
	}

	/* send the request and get the reply */
	return (send_recv_process(op_string, rpc));
}

