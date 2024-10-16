#include <pthread.h>
#include "cna.h"
#include <assert.h>
 
#ifndef NTHREADS
#define NTHREADS 3
#endif

__thread intptr_t tindex;

cna_lock_t lock;
cna_node_t node[NTHREADS];
char shared = 0;
char sum = 0;

void *thread_n(void *arg)
{
    tindex = ((intptr_t) arg);
    cna_lock(&lock, &node[tindex]);
    shared = tindex;
    char r = shared;
    assert(r == tindex);
    sum++;
    cna_unlock(&lock, &node[tindex]);
    return NULL;
}
 
int main()
{
    pthread_t t[NTHREADS];
 
    for (char i = 0; i < NTHREADS; i++)
        pthread_create(&t[i], 0, thread_n, (void *)(size_t)i);

    for (char i = 0; i < NTHREADS; i++)
        pthread_join(t[i], 0);

    assert(sum == NTHREADS);
 
    return 0;
}

