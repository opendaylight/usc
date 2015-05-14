/*
 * salt.c - generate a random salt string for crypt()
 *
 * Written by Marek Michalkiewicz <marekm@i17linuxb.ists.pwr.wroc.pl>,
 * it is in the public domain.
 *
 * l64a was Written by J.T. Conklin <jtc@netbsd.org>. Public domain.
 */

#ident "$Id: salt.c 3489 2011-09-18 20:40:50Z nekral-guest $"

#include <sys/time.h>
#include <stdlib.h>
#include <stdio.h>
#include <assert.h>
#include <errno.h>
#include <sys/types.h>
#include <unistd.h>
#include <string.h>

/* local function prototypes */
static void seedRNG (void);
static /*@observer@*/const char *gensalt (size_t salt_size);
static size_t SHA_salt_size (void);
static /*@observer@*/const char *SHA_salt_rounds (/*@null@*/int *prefered_rounds);

#ifndef HAVE_L64A
static /*@observer@*/char *l64a(long value)
{
	static char buf[8];
	char *s = buf;
	int digit;
	int i;

	if (value < 0) {
		errno = EINVAL;
		return(NULL);
	}

	for (i = 0; value != 0 && i < 6; i++) {
		digit = value & 0x3f;

		if (digit < 2) {
			*s = digit + '.';
		} else if (digit < 12) {
			*s = digit + '0' - 2;
		} else if (digit < 38) {
			*s = digit + 'A' - 12;
		} else {
			*s = digit + 'a' - 38;
		}

		value >>= 6;
		s++;
	}

	*s = '\0';

	return(buf);
}
#endif /* !HAVE_L64A */

static void seedRNG (void)
{
	struct timeval tv;
	static int seeded = 0;

	if (0 == seeded) {
		(void) gettimeofday (&tv, NULL);
		srandom (tv.tv_sec ^ tv.tv_usec ^ getpid ());
		seeded = 1;
	}
}

/*
 * Add the salt prefix.
 */
#define MAGNUM(array,ch)	(array)[0]=(array)[2]='$',(array)[1]=(ch),(array)[3]='\0'

/*
 * Return the salt size.
 * The size of the salt string is between 8 and 16 bytes for the SHA crypt
 * methods.
 */
static size_t SHA_salt_size (void)
{
	double rand_size;
	seedRNG ();
	rand_size = (double) 9.0 * random () / RAND_MAX;
	return (size_t) (8 + rand_size);
}

/* Default number of rounds if not explicitly specified.  */
#define ROUNDS_DEFAULT 5000
/* Minimum number of rounds.  */
#define ROUNDS_MIN 1000
/* Maximum number of rounds.  */
#define ROUNDS_MAX 999999999

/* from local_users.h */
extern long sha_crypt_min_rounds; /* default is -1 */
extern long sha_crypt_max_rounds; /* default is -1 */
extern char *encrypt_method; /* default is NULL */
extern char md5_crypt_enab; /* default is 0 */

/*
 * Return a salt prefix specifying the rounds number for the SHA crypt methods.
 */
static /*@observer@*/const char *SHA_salt_rounds (/*@null@*/int *prefered_rounds)
{
	static char rounds_prefix[18]; /* Max size: rounds=999999999$ */
	long rounds;

	if (NULL == prefered_rounds) {
		double rand_rounds;

		if ((-1 == sha_crypt_min_rounds) && (-1 == sha_crypt_max_rounds)) {
			return "";
		}

		if (-1 == sha_crypt_min_rounds) {
			sha_crypt_min_rounds = sha_crypt_max_rounds;
		}

		if (-1 == sha_crypt_max_rounds) {
			sha_crypt_max_rounds = sha_crypt_min_rounds;
		}

		if (sha_crypt_min_rounds > sha_crypt_max_rounds) {
			sha_crypt_max_rounds = sha_crypt_min_rounds;
		}

		seedRNG ();
		rand_rounds = (double) (sha_crypt_max_rounds-sha_crypt_min_rounds+1.0) * random ();
		rand_rounds /= RAND_MAX;
		rounds = sha_crypt_min_rounds + rand_rounds;
	} else if (0 == *prefered_rounds) {
		return "";
	} else {
		rounds = *prefered_rounds;
	}

	/* Sanity checks. The libc should also check this, but this
	 * protects against a rounds_prefix overflow. */
	if (rounds < ROUNDS_MIN) {
		rounds = ROUNDS_MIN;
	}

	if (rounds > ROUNDS_MAX) {
		rounds = ROUNDS_MAX;
	}

	(void) snprintf (rounds_prefix, sizeof rounds_prefix,
	                 "rounds=%ld$", rounds);

	return rounds_prefix;
}

/*
 *  Generate salt of size salt_size.
 */
#define MAX_SALT_SIZE 16
#define MIN_SALT_SIZE 8

static /*@observer@*/const char *gensalt (size_t salt_size)
{
	static char salt[32];

	salt[0] = '\0';

	assert (salt_size >= MIN_SALT_SIZE &&
	        salt_size <= MAX_SALT_SIZE);
	seedRNG ();
	strcat (salt, l64a (random()));
	do {
		strcat (salt, l64a (random()));
	} while (strlen (salt) < salt_size);

	salt[salt_size] = '\0';

	return salt;
}

/*
 * Generate 8 base64 ASCII characters of random salt.  If MD5_CRYPT_ENAB
 * in /etc/login.defs is "yes", the salt string will be prefixed by "$1$"
 * (magic) and pw_encrypt() will execute the MD5-based FreeBSD-compatible
 * version of crypt() instead of the standard one.
 * Other methods can be set with ENCRYPT_METHOD
 *
 * The method can be forced with the meth parameter.
 * If NULL, the method will be defined according to the MD5_CRYPT_ENAB and
 * ENCRYPT_METHOD login.defs variables.
 *
 * If meth is specified, an additional parameter can be provided.
 *  * For the SHA256 and SHA512 method, this specifies the number of rounds
 *    (if not NULL).
 */
/*@observer@*/const char *crypt_make_salt (/*@null@*//*@observer@*/const char *meth, /*@null@*/void *arg)
{
	/* Max result size for the SHA methods:
	 *  +3		$5$
	 *  +17		rounds=999999999$
	 *  +16		salt
	 *  +1		\0
	 */
	static char result[40];
	size_t salt_len = 8;
	const char *method;

	result[0] = '\0';

	if (NULL != meth)
		method = meth;
	else {
		method = encrypt_method;
		if (NULL == method) {
			method = (md5_crypt_enab == 1) ? "MD5" : "DES";
		}
	}

	if (0 == strcmp (method, "MD5")) {
		MAGNUM(result, '1');
	} else if (0 == strcmp (method, "SHA256")) {
		MAGNUM(result, '5');
		strcat(result, SHA_salt_rounds((int *)arg));
		salt_len = SHA_salt_size();
	} else if (0 == strcmp (method, "SHA512")) {
		MAGNUM(result, '6');
		strcat(result, SHA_salt_rounds((int *)arg));
		salt_len = SHA_salt_size();
	} else if (0 != strcmp (method, "DES")) {
		fprintf (stderr,
			 "Invalid ENCRYPT_METHOD value: '%s'.\n"
			   "Defaulting to DES.\n",
			 method);
		result[0] = '\0';
	}

	/*
	 * Concatenate a pseudo random salt.
	 */
	assert (sizeof (result) > strlen (result) + salt_len);
	strncat (result, gensalt (salt_len),
		 sizeof (result) - strlen (result) - 1);

	return result;
}
