#include <pthread.h>
#include "ticket_awnsb_mutex.h"
#include <assert.h>

#ifndef NTHREADS
#define NTHREADS 3
#endif

char shared;
ticket_awnsb_mutex_t lock;
char sum = 0;

void *thread_n(void *arg)
{
    intptr_t index = ((intptr_t) arg);

    ticket_awnsb_mutex_lock(&lock);
    shared = index;
    char r = shared;
    assert(r == index);
    sum++;
    ticket_awnsb_mutex_unlock(&lock);
    return NULL;
}

int main()
{
    pthread_t t[NTHREADS];

    ticket_awnsb_mutex_init(&lock, DEFAULT_MAX_WAITERS);

    for (char i = 0; i < NTHREADS; i++)
        pthread_create(&t[i], 0, thread_n, (void *)(size_t)i);

    for (char i = 0; i < NTHREADS; i++)
        pthread_join(t[i], 0);

    assert(sum == NTHREADS);

    return 0;
}
