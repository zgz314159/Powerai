#pragma once
#include <stddef.h>

extern "C" {

// Prototype API for later FAISS integration
bool faiss_init();
int faiss_open_index(const char* path);
void faiss_close_index(int handle);
int faiss_search(int handle, const float* query, int dim, int k, int* out_ids, float* out_scores);

}
