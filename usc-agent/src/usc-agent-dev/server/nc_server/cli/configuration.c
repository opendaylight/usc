/*
 * configuration.c
 * Author Radek Krejci <rkrejci@cesnet.cz>
 *
 * NETCONF client configuration.
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
#include <assert.h>
#include <stdlib.h>
#include <stdio.h>
#include <readline/readline.h>
#include <readline/history.h>

#include <sys/types.h>
#include <sys/stat.h>
#include <unistd.h>
#include <pwd.h>
#include <errno.h>
#include <fcntl.h>
#include <dirent.h>

#include <libnetconf.h>
#include <libnetconf_ssh.h>

#include <libxml/tree.h>

#include "configuration.h"
#include "commands.h"

static const char rcsid[] __attribute__((used)) ="$Id: "__FILE__": "RCSID" $";

/* NetConf Client home (appended to ~/) */
#define NCC_DIR ".netopeer-cli"
/* all these appended to NCC_DIR */
#define CA_DIR "certs"
#define CRL_DIR "crl"
#define CERT_CRT "client.crt"
#define CERT_PEM "client.pem"
#define CERT_KEY "client.key"

char* get_netconf_dir(void)
{
	int ret;
	struct passwd * pw;
	char* user_home, *netconf_dir;

	if ((pw = getpwuid(getuid())) == NULL) {
		ERROR("get_netconf_dir", "Determining home directory failed: getpwuid: %s.", strerror(errno));
		return NULL;
	}
	user_home = pw->pw_dir;

	if (asprintf(&netconf_dir, "%s/%s", user_home, NCC_DIR) == -1) {
		ERROR("get_netconf_dir", "asprintf() failed (%s:%d).", __FILE__, __LINE__);
		return NULL;
	}

	ret = eaccess(netconf_dir, R_OK | X_OK);
	if (ret == -1) {
		if (errno == ENOENT) {
			/* directory does not exist */
			ERROR("get_netconf_dir", "Configuration directory \"%s\" does not exist, creating it.", netconf_dir);
			if (mkdir(netconf_dir, 0700) != 0) {
				ERROR("get_netconf_dir", "Configuration directory \"%s\" cannot be created: %s", netconf_dir, strerror(errno));
				free(netconf_dir);
				return NULL;
			}
		} else {
			ERROR("get_netconf_dir", "Configuration directory \"%s\" exists but something else failed: %s", netconf_dir, strerror(errno));
			free(netconf_dir);
			return NULL;
		}
	}

	return netconf_dir;
}

void get_default_client_cert(char** cert, char** key)
{
	char* netconf_dir;
	struct stat st;
	int ret;

	assert(cert && !*cert);
	assert(key &&!*key);

	if ((netconf_dir = get_netconf_dir()) == NULL) {
		return;
	}

	// trying to use *.crt and *.key format
	if (asprintf(cert, "%s/%s", netconf_dir, CERT_CRT) == -1 || asprintf(key, "%s/%s", netconf_dir, CERT_KEY) == -1) {
		ERROR("get_default_client_cert", "asprintf() failed (%s:%d).", __FILE__, __LINE__);
		ERROR("get_default_client_cert", "Unable to use the default client certificate due to the previous error.");
		free(netconf_dir);
		return;
	}

	if (eaccess(*cert, R_OK) == -1 || eaccess(*key, R_OK) == -1) {
		if (errno != ENOENT) {
			ERROR("get_default_client_cert", "Unable to access \"%s\" and \"%s\": %s", *cert, *key, strerror(errno));
			free(*key);
			*key = NULL;
			free(*cert);
			*cert = NULL;
			free(netconf_dir);
			return;
		}

		// *.crt & *.key failed, trying to use *.pem format
		free(*key);
		*key = NULL;
		free(*cert);
		if (asprintf(cert, "%s/%s", netconf_dir, CERT_PEM) == -1) {
			ERROR("get_default_client_cert", "asprintf() failed (%s:%d).", __FILE__, __LINE__);
			ERROR("get_default_client_cert", "Unable to use the default client certificate due to the previous error.");
			free(netconf_dir);
			return;
		}

		ret = eaccess(*cert, R_OK);
		if (ret == -1) {
			if (errno != ENOENT) {
				ERROR("get_default_client_cert", "Unable to access \"%s\": %s", *cert, strerror(errno));
			} else {
				// *.pem failed as well
				ERROR("get_default_client_cert", "Unable to find the default client certificate.");
			}
			free(*cert);
			*cert = NULL;
			free(netconf_dir);
			return;
		} else {
			/* check permissions on *.pem */
			if (stat(*cert, &st) != 0) {
				ERROR("get_default_client_cert", "Stat on \"%s\" failed: %s", *cert, strerror(errno));
			} else if (st.st_mode & 0066) {
				ERROR("get_default_client_cert", "Unsafe permissions on \"%s\"", *cert);
			}
		}
	} else {
		/* check permissions on *.key */
		if (stat(*key, &st) != 0) {
			ERROR("get_default_client_cert", "Stat on \"%s\" failed: %s", *key, strerror(errno));
		} else if (st.st_mode & 0066) {
			ERROR("get_default_client_cert", "Unsafe permissions on \"%s\"", *key);
		}
	}

	free(netconf_dir);
	return;
}

char* get_default_trustedCA_dir(DIR** ret_dir)
{
	char* netconf_dir, *cert_dir;

	if ((netconf_dir = get_netconf_dir()) == NULL) {
		return NULL;
	}

	if (asprintf(&cert_dir, "%s/%s", netconf_dir, CA_DIR) == -1) {
		ERROR("get_default_trustedCA_dir", "asprintf() failed (%s:%d).", __FILE__, __LINE__);
		ERROR("get_default_trustedCA_dir", "Unable to use the trusted CA directory due to the previous error.");
		free(netconf_dir);
		return NULL;
	}
	free(netconf_dir);

	if (ret_dir != NULL) {
		if ((*ret_dir = opendir(cert_dir)) == NULL) {
			ERROR("get_default_trustedCA_dir", "Unable to open the default trusted CA directory: %s", strerror(errno));
			free(cert_dir);
			return NULL;
		}
		return NULL;
	}

	if (eaccess(cert_dir, R_OK | W_OK | X_OK) < 0) {
		ERROR("get_default_trustedCA_dir", "Unable to fully access the default trusted CA directory: %s", strerror(errno));
		free(cert_dir);
		return NULL;
	}
	return cert_dir;
}

char* get_default_CRL_dir(DIR** ret_dir)
{
	char* netconf_dir, *crl_dir;

	if ((netconf_dir = get_netconf_dir()) == NULL) {
		return NULL;
	}

	if (asprintf(&crl_dir, "%s/%s", netconf_dir, CRL_DIR) == -1) {
		ERROR("get_default_CRL_dir", "asprintf() failed (%s:%d).", __FILE__, __LINE__);
		ERROR("get_default_CRL_dir", "Unable to use the trusted CA directory due to the previous error.");
		free(netconf_dir);
		return NULL;
	}
	free(netconf_dir);

	if (ret_dir != NULL) {
		if ((*ret_dir = opendir(crl_dir)) == NULL) {
			ERROR("get_default_CRL_dir", "Unable to open the default CRL directory: %s", strerror(errno));
			free(crl_dir);
			return NULL;
		}
		return NULL;
	}

	if (eaccess(crl_dir, R_OK | W_OK | X_OK) < .0) {
		ERROR("get_default_CRL_dir", "Unable to fully access the default CRL directory: %s", strerror(errno));
		free(crl_dir);
		return NULL;
	}
	return crl_dir;
}

void load_config(struct nc_cpblts **cpblts)
{
	char * netconf_dir, *history_file, *config_file;
#ifdef ENABLE_TLS
	struct stat st;
	char* trusted_dir, *crl_dir;
#endif
	char * tmp_cap;
	int ret, history_fd, config_fd;
	xmlDocPtr config_doc;
	xmlNodePtr config_cap, tmp_node;

#ifndef DISABLE_LIBSSH
	char * key_priv, *key_pub, *prio;
	xmlNodePtr tmp_auth, tmp_pref, tmp_key;
#endif

	assert(cpblts);

	(*cpblts) = nc_session_get_cpblts_default();

	if ((netconf_dir = get_netconf_dir()) == NULL) {
		return;
	}

#ifdef ENABLE_TLS
	if (asprintf (&trusted_dir, "%s/%s", netconf_dir, CA_DIR) == -1) {
		ERROR("load_config", "asprintf() failed (%s:%d).", __FILE__, __LINE__);
		ERROR("load_config", "Unable to check trusted CA directory due to the previous error.");
		trusted_dir = NULL;
	} else {
		if (stat(trusted_dir, &st) == -1) {
			if (errno == ENOENT) {
				ERROR("load_config", "Trusted CA directory (%s) does not exist, creating it", trusted_dir);
				if (mkdir(trusted_dir, 0700) == -1) {
					ERROR("load_config", "Trusted CA directory cannot be created (%s)", strerror(errno));
				}
			} else {
				ERROR("load_config", "Accessing the trusted CA directory failed (%s)", strerror(errno));
			}
		} else {
			if (!S_ISDIR(st.st_mode)) {
				ERROR("load_config", "Accessing the trusted CA directory failed (Not a directory)");
			}
		}
	}
	free(trusted_dir);

	if (asprintf (&crl_dir, "%s/%s", netconf_dir, CRL_DIR) == -1) {
		ERROR("load_config", "asprintf() failed (%s:%d).", __FILE__, __LINE__);
		ERROR("load_config", "Unable to check CRL directory due to the previous error.");
		crl_dir = NULL;
	} else {
		if (stat(crl_dir, &st) == -1) {
			if (errno == ENOENT) {
				ERROR("load_config", "CRL directory (%s) does not exist, creating it", crl_dir);
				if (mkdir(crl_dir, 0700) == -1) {
					ERROR("load_config", "CRL directory cannot be created (%s)", strerror(errno));
				}
			} else {
				ERROR("load_config", "Accessing the CRL directory failed (%s)", strerror(errno));
			}
		} else {
			if (!S_ISDIR(st.st_mode)) {
				ERROR("load_config", "Accessing the CRL directory failed (Not a directory)");
			}
		}
	}
	free(crl_dir);
#endif /* ENABLE_TLS */

	if (asprintf(&history_file, "%s/history", netconf_dir) == -1) {
		ERROR("load_config", "asprintf() failed (%s:%d).", __FILE__, __LINE__);
		ERROR("load_config", "Unable to load commands history due to the previous error.");
		history_file = NULL;
	} else {
		ret = eaccess(history_file, R_OK);
		if (ret == -1) {
			if (errno == ENOENT) {
				ERROR("load_config", "History file (%s) does not exist, creating it", history_file);
				if ((history_fd = creat(history_file, 0600)) == -1) {
					ERROR("load_config", "History file cannot be created (%s)", strerror(errno));
				} else {
					close(history_fd);
				}
			} else {
				ERROR("load_config", "Accessing the history file failed (%s)", strerror(errno));
			}
		} else {
			/* file exist and is accessible */
			if (read_history(history_file)) {
				ERROR("load_config", "Failed to load history.");
			}
		}
	}

	if (asprintf(&config_file, "%s/config.xml", netconf_dir) == -1) {
		ERROR("load_config", "asprintf() failed (%s:%d).", __FILE__, __LINE__);
		ERROR("load_config", "Unable to load configuration due to the previous error.");
		config_file = NULL;
	} else {
		ret = eaccess(config_file, R_OK);
		if (ret == -1) {
			if (errno == ENOENT) {
				ERROR("load_config", "Configuration file (%s) does not exits, creating it", config_file);
				if ((config_fd = creat(config_file, 0600)) == -1) {
					ERROR("load_config", "Configuration file cannot be created (%s)", strerror(errno));
				} else {
					close(config_fd);
				}
			} else {
				ERROR("load_config", "Configuration file cannot accessed (%s)", strerror(errno));
			}
		} else {
			/* file exist and is accessible */
			if ((config_doc = xmlReadFile(config_file, NULL, XML_PARSE_NOBLANKS | XML_PARSE_NSCLEAN)) == NULL) {
				ERROR("load_config", "Failed to load configuration of NETCONF client (xmlReadFile failed).");
			} else {
				/* doc -> <netconf-client/>*/
				if (config_doc->children != NULL && xmlStrEqual(config_doc->children->name, BAD_CAST "netconf-client")) {
					tmp_node = config_doc->children->children;
					while (tmp_node) {
						if (xmlStrEqual(tmp_node->name, BAD_CAST "capabilities")) {
							/* doc -> <netconf-client> -> <capabilities> */
							nc_cpblts_free(*cpblts);
							(*cpblts) = nc_cpblts_new(NULL);
							config_cap = tmp_node->children;
							while (config_cap) {
								tmp_cap = (char *) xmlNodeGetContent(config_cap);
								nc_cpblts_add(*cpblts, tmp_cap);
								free(tmp_cap);
								config_cap = config_cap->next;
							}
						}
#ifndef DISABLE_LIBSSH
						else if (xmlStrEqual(tmp_node->name, BAD_CAST "authentication")) {
							/* doc -> <netconf-client> -> <authentication> */
							tmp_auth = tmp_node->children;
							while (tmp_auth) {
								if (xmlStrEqual(tmp_auth->name, BAD_CAST "pref")) {
									tmp_pref = tmp_auth->children;
									while (tmp_pref) {
										prio = (char*) xmlNodeGetContent(tmp_pref);
										if (xmlStrEqual(tmp_pref->name, BAD_CAST "publickey")) {
											nc_ssh_pref(NC_SSH_AUTH_PUBLIC_KEYS, atoi(prio));
										} else if (xmlStrEqual(tmp_pref->name, BAD_CAST "interactive")) {
											nc_ssh_pref(NC_SSH_AUTH_INTERACTIVE, atoi(prio));
										} else if (xmlStrEqual(tmp_pref->name, BAD_CAST "password")) {
											nc_ssh_pref(NC_SSH_AUTH_PASSWORD, atoi(prio));
										}
										free(prio);
										tmp_pref = tmp_pref->next;
									}
								} else if (xmlStrEqual(tmp_auth->name, BAD_CAST "keys")) {
									tmp_key = tmp_auth->children;
									while (tmp_key) {
										if (xmlStrEqual(tmp_key->name, BAD_CAST "key-path")) {
											key_priv = (char*) xmlNodeGetContent(tmp_key);
											if (asprintf(&key_pub, "%s.pub", key_priv) == -1) {
												ERROR("load_config", "asprintf() failed (%s:%d).", __FILE__, __LINE__);
												ERROR("load_config", "Unable to set SSH keys pair due to the previous error.");
												key_pub = NULL;
												tmp_key = tmp_key->next;
												continue;
											}
											nc_set_keypair_path(key_priv, key_pub);
											free(key_priv);
											free(key_pub);
										}
										tmp_key = tmp_key->next;
									}
								}
								tmp_auth = tmp_auth->next;
							}
						}
#endif /* not DISABLE_LIBSSH */
						tmp_node = tmp_node->next;
					}
				}
				xmlFreeDoc(config_doc);
			}
		}
	}

	free(config_file);
	free(history_file);
	free(netconf_dir);
}

/**
 * \brief Store configuration and history
 */
void store_config(struct nc_cpblts *cpblts)
{
	char *netconf_dir, *history_file, *config_file;
	const char * cap;
	int history_fd, ret;
	xmlDocPtr config_doc;
	xmlNodePtr config_caps;
	FILE *config_f;

	if ((netconf_dir = get_netconf_dir()) == NULL) {
		return;
	}

	if (asprintf(&history_file, "%s/history", netconf_dir) == -1) {
		ERROR("store_config", "asprintf() failed (%s:%d).", __FILE__, __LINE__);
		ERROR("store_config", "Unable to store commands history due to the previous error.");
		history_file = NULL;
	} else {
		ret = eaccess(history_file, R_OK | W_OK);
		if (ret == -1) {
			if (errno == ENOENT) {
				/* file does not exit, create it */
				if ((history_fd = creat(history_file, 0600)) == -1) {
					/* history file can not be created */
				} else {
					close(history_fd);
				}
			}
			ERROR("store_config", "Accessing the history file failed (%s)", strerror(errno));
		}

		if (write_history(history_file)) {
			ERROR("save_config", "Failed to save history.");
		}
		free(history_file);
	}

	if (asprintf(&config_file, "%s/config.xml", netconf_dir) == -1) {
		ERROR("store_config", "asprintf() failed (%s:%d).", __FILE__, __LINE__);
		ERROR("store_config", "Unable to store configuration due to the previous error.");
		config_file = NULL;
	} else {
		if (eaccess(config_file, R_OK | W_OK) == -1 || (config_doc = xmlReadFile(config_file, NULL, XML_PARSE_NOBLANKS | XML_PARSE_NSCLEAN | XML_PARSE_NOERROR)) == NULL) {
			config_doc = xmlNewDoc(BAD_CAST "1.0");
			config_doc->children = xmlNewDocNode(config_doc, NULL, BAD_CAST "netconf-client", NULL);
		}
		if (config_doc != NULL) {
			if (config_doc->children != NULL && xmlStrEqual(config_doc->children->name, BAD_CAST "netconf-client")) {
				config_caps = config_doc->children->children;
				while (config_caps != NULL && !xmlStrEqual(config_caps->name, BAD_CAST "capabilities")) {
					config_caps = config_caps->next;
				}
				if (config_caps != NULL) {
					xmlUnlinkNode(config_caps);
					xmlFreeNode(config_caps);
				}
				config_caps = xmlNewChild(config_doc->children, NULL, BAD_CAST "capabilities", NULL);
				nc_cpblts_iter_start(cpblts);
				while ((cap = nc_cpblts_iter_next(cpblts)) != NULL) {
					xmlNewChild(config_caps, NULL, BAD_CAST "capability", BAD_CAST cap);
				}
			}
			if ((config_f = fopen(config_file, "w")) == NULL || xmlDocFormatDump(config_f, config_doc, 1) < 0) {
				ERROR("store_config", "Cannot write configuration to file %s", config_file);
			} else {
				fclose(config_f);
			}
			xmlFreeDoc(config_doc);
		} else {
			ERROR("store_config", "Cannot write configuration to file %s", config_file);
		}
	}

	free(netconf_dir);
	free(config_file);
}
