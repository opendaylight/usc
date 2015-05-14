/**
 * \file common.c
 * \brief Internal functions for cfgsystem module
 * \author Radek Krejci <rkrejci@cesnet.cz>
 * \date 2014
 *
 * Copyright (C) 2014 CESNET
 *
 * LICENSE TERMS
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
 * This software is provided ``as is'', and any express or implied
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

#include <assert.h>
#include <unistd.h>
#include <stdlib.h>
#include <stdio.h>
#include <string.h>

#include <augeas.h>

#include "common.h"

augeas *sysaugeas = NULL;

char NETOPEER_SSHD_CONF[sizeof(NETOPEER_DIR) + 12];

static void clip_occurences_with(char *str, char sought, char replacement)
{
	int adjacent = 0;
	int clipped = 0;

	assert(str);

	while (*str != '\0') {
		if (*str != sought) {
			if (clipped != 0) {
				/* Hurl together. */
				*(str - clipped) = *str;
			}
			adjacent = 0;
		} else if (adjacent == 0) {
			/*
			 * Found first character from a possible sequence of
			 * characters. The whole sequence is going to be
			 * replaced by only one replacement character.
			 */
			*(str - clipped) = replacement;
			/* Next occurrence will be adjacent. */
			adjacent = 1;
		} else {
			++clipped;
		}

		/* Next character. */
		++str;
	}

	if (clipped != 0) {
		/* New string end. */
		*(str - clipped) = '\0';
	}
}

int augeas_init(char** msg)
{
	char** matches;
	char* path = NULL;
	int c, i;

	assert(msg);

	if (sysaugeas != NULL) {
		/* already initiated */
		return EXIT_SUCCESS;
	}

	sysaugeas = aug_init(NULL, NULL, AUG_NO_MODL_AUTOLOAD | AUG_NO_ERR_CLOSE);
	if (aug_error(sysaugeas) != AUG_NOERROR) {
		asprintf(msg, "Augeas NTP initialization failed (%s)", aug_error_message(sysaugeas));
		return EXIT_FAILURE;
	}

	/*
	 * check if we are able to load Login_defs lens, on augeas <1.1 it can fail,
	 * but we can continue even without login.defs (default values will be used)
	 */
	aug_set(sysaugeas, "/augeas/load/Login_defs/lens", "Login_defs.lns");
	aug_set(sysaugeas, "/augeas/load/Login_defs/incl", AUGEAS_LOGIN_CONF);
	aug_load(sysaugeas);
	if (aug_match(sysaugeas, "/augeas//error", NULL) != 0) {
		/* continue without login.defs */
		aug_rm(sysaugeas, "/augeas/load/Login_defs/");
	}

	/* NTP */
	aug_set(sysaugeas, "/augeas/load/Ntp/lens", "Ntp.lns");
	aug_set(sysaugeas, "/augeas/load/Ntp/incl", AUGEAS_NTP_CONF);
	/* DNS resolver */
	aug_set(sysaugeas, "/augeas/load/Resolv/lens", "Resolv.lns");
	aug_set(sysaugeas, "/augeas/load/Resolv/incl", AUGEAS_DNS_CONF);
	/* authentication */
	strcpy(NETOPEER_SSHD_CONF, NETOPEER_DIR"/sshd_config");
	clip_occurences_with(NETOPEER_SSHD_CONF, '/', '/');
	aug_set(sysaugeas, "/augeas/load/Sshd/lens", "Sshd.lns");
	aug_set(sysaugeas, "/augeas/load/Sshd/incl", NETOPEER_SSHD_CONF);
	aug_load(sysaugeas);

	if ((c = aug_match(sysaugeas, "/augeas//error", &matches)) != 0) {
		aug_get(sysaugeas, matches[0], (const char**) msg);
		asprintf(msg, "Initiating augeas failed (%s: %s)", matches[0], *msg);
		augeas_close();
		for (i = 0; i < c; ++i) {
			free(matches[i]);
		}
		free(matches);
		return EXIT_FAILURE;
	}

	/* Switch off the PAM authentication in the sshd configuration.
	 * The better way should be probably support PAM, but since we support only
	 * local users, we don't need it. The only configuration we have to work
	 * with is sshd_config. There, if the UsePAM is set to 'no', we have a full
	 * control of the authentication via PasswordAuthentication value which
	 * allows us to turn on/off 'local-users' user-authentication-order.
	 *
	 * If the PasswordAuthentication is 'no', pubkey authentication can still
	 * works (but it is out of the ietf-netmod-system-mgmt). In this case the
	 * user-authentication-order leaf-list with 'local-users' value is not
	 * present.
	 *
	 * If the PasswordAuthentication is 'yes', the user-authentication-order
	 * leaf-list with 'local-users' value is present. And since we don't support
	 * radius authentication, it is the only user-authentication-order element.
	 */
	asprintf(&path, "/files/%s/UsePAM", NETOPEER_SSHD_CONF);
	aug_set(sysaugeas, path, "no");
	free(path);
	augeas_save(msg);
	free(*msg); *msg = NULL;

	return EXIT_SUCCESS;
}

int augeas_save(char** msg)
{
	if (aug_save(sysaugeas) != 0) {
		asprintf(msg, "Saving configuration failed (%s)", aug_error_message(sysaugeas));
		return (EXIT_FAILURE);
	}

	return (EXIT_SUCCESS);
}

void augeas_close(void)
{
	aug_close(sysaugeas);
	sysaugeas = NULL;
}
