#include <stdatomic.h>

#ifdef ACQ2RX_FUTEX
#define mo_wait memory_order_relaxed
#else
#define mo_wait memory_order_acquire
#endif

#ifdef REL2RX_FUTEX
#define mo_wake memory_order_relaxed
#else
#define mo_wake memory_order_release
#endif

static atomic_char sig;

static inline void __futex_wait(atomic_char *m, char v)
{
    char s = atomic_load_explicit(&sig, mo_wait);
    if (atomic_load_explicit(m, mo_wait) != v)
        return;

    while (atomic_load_explicit(&sig, mo_wait) == s)
        ;
}

static inline void __futex_wake(atomic_char *m, char v)
{
    atomic_fetch_add_explicit(&sig, 1, mo_wake);
}