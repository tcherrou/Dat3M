#include <pthread.h>
#include "mutex_musl.h"
#include <assert.h>

#ifndef NTHREADS
#define NTHREADS 3
#endif

char shared;
mutex_t mutex;
char sum = 0;

void *thread_n(void *arg)
{
    intptr_t index = ((intptr_t) arg);

    mutex_lock(&mutex);
    shared = index;
    char r = shared;
    assert(r == index);
    sum++;
    mutex_unlock(&mutex);
    return NULL;
}

int main()
{
    pthread_t t[NTHREADS];

    mutex_init(&mutex);

    for (char i = 0; i < NTHREADS; i++)
        pthread_create(&t[i], 0, thread_n, (void *)(size_t)i);

    for (char i = 0; i < NTHREADS; i++)
        pthread_join(t[i], 0);

    assert(sum == NTHREADS);

    return 0;
}
