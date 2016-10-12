__constant sampler_t sampler =
      CLK_NORMALIZED_COORDS_FALSE
    | CLK_ADDRESS_CLAMP_TO_EDGE
    | CLK_FILTER_NEAREST;
long F(int a, int b, int c) {
	return ((long)(a + b + c - 3))*a*b*c;
}
struct cl_Tripartition {
	__global const long * cluster1;
	__global const long * cluster2;
	__global const long * cluster3;
};
inline uint popcnt(const ulong i) {
        uint n;
        asm("popc.b64 %0, %1;" : "=r"(n) : "l" (i));
        return n;
}
int bitIntersectionSize(__global const long input1[SPECIES_WORD_LENGTH], __global const long input2[SPECIES_WORD_LENGTH]) {
	int out = 0;
	for (int i = 0; i < SPECIES_WORD_LENGTH; i++) {
		out += popcnt(input1[i]&input2[i]);
	}
	return out;
}
__kernel void calcWeight(
	__read_only image2d_t geneTreesAsInts,
	int geneTreesAsIntsLength,
	__global const long* allArray,
	__global const long* tripartitions1glob,
	__global const long* tripartitions2glob,
	__global const long* tripartitions3glob,
	__global long* weightArray

){
	long weight = 0;
	struct cl_Tripartition trip;
	int idx = get_global_id(0);
	
	trip.cluster1 = tripartitions1glob + idx * SPECIES_WORD_LENGTH;
	trip.cluster2 = tripartitions2glob + idx * SPECIES_WORD_LENGTH;
	trip.cluster3 = tripartitions3glob + idx * SPECIES_WORD_LENGTH;
	
	int allsides[3];
	

	int newTree = 1;
	int counter = 0;
	int treeCounter = 0;

	int stack[(STACK_SIZE + 2) * 3];
	int overlap [(STACK_SIZE + 1) * 3];
	int overlapind [(STACK_SIZE + 1) * 3];
	
	int top = 0;
	int4 geneTreesAsInts4Ints;
	
	while(1){
		//printf("%d %d %d\n", counter, IMAGE_WIDTH, geneTreesAsIntsLength);
		geneTreesAsInts4Ints = read_imagei(geneTreesAsInts, sampler, (int2)((counter / 4) % IMAGE_WIDTH, counter / 4 / IMAGE_WIDTH));
		/*
		if(0 && idx == 0){
			if(geneTreesAsInts2[counter] != geneTreesAsInts4Ints.x)
				printf("x %d %d %d %d\n", counter, geneTreesAsIntsLength, geneTreesAsInts2[counter], geneTreesAsInts4Ints.x);
			if(geneTreesAsInts2[counter + 1] != geneTreesAsInts4Ints.y)
				printf("y %d %d %d %d\n", counter + 1, geneTreesAsIntsLength, geneTreesAsInts2[counter + 1], geneTreesAsInts4Ints.y);
			if(geneTreesAsInts2[counter + 2] != geneTreesAsInts4Ints.z)
				printf("z %d %d %d %d\n", counter + 2, geneTreesAsIntsLength, geneTreesAsInts2[counter + 2], geneTreesAsInts4Ints.z);
			if(geneTreesAsInts2[counter + 3] != geneTreesAsInts4Ints.w)
				printf("w %d %d %d %d\n", counter + 3, geneTreesAsIntsLength, geneTreesAsInts2[counter + 3], geneTreesAsInts4Ints.w);
		}
		*/
		if(counter < geneTreesAsIntsLength) {
			counter++;
			if (newTree) {
				newTree = 0;

				allsides[0] = bitIntersectionSize(&allArray[treeCounter * SPECIES_WORD_LENGTH], trip.cluster1);
				allsides[1] = bitIntersectionSize(&allArray[treeCounter * SPECIES_WORD_LENGTH], trip.cluster2);
				allsides[2] = bitIntersectionSize(&allArray[treeCounter * SPECIES_WORD_LENGTH], trip.cluster3);

				treeCounter++;

			}
			if (geneTreesAsInts4Ints.x >= 0) {
				stack[top] = ((trip.cluster1[SPECIES_WORD_LENGTH - 1 - geneTreesAsInts4Ints.x / LONG_BIT_LENGTH])>>(geneTreesAsInts4Ints.x % LONG_BIT_LENGTH)) & 1;
				stack[top + (STACK_SIZE + 2)] = ((trip.cluster2[SPECIES_WORD_LENGTH - 1 - geneTreesAsInts4Ints.x / LONG_BIT_LENGTH])>>(geneTreesAsInts4Ints.x % LONG_BIT_LENGTH)) & 1;
				stack[top + (STACK_SIZE + 2) * 2] = ((trip.cluster3[SPECIES_WORD_LENGTH - 1 - geneTreesAsInts4Ints.x / LONG_BIT_LENGTH])>>(geneTreesAsInts4Ints.x % LONG_BIT_LENGTH)) & 1;
				top++;
			}
			else if (geneTreesAsInts4Ints.x == INT_MIN) {
				top = 0;
				newTree = 1;
			}
			else if (geneTreesAsInts4Ints.x == -2) {
				top--;
				int topminus1 = top - 1;
				int newSides0 = stack[top] + stack[topminus1];
				int newSides1 = stack[top + (STACK_SIZE + 2)] + stack[topminus1 + (STACK_SIZE + 2)];
				int newSides2 = stack[top + (STACK_SIZE + 2) * 2] + stack[topminus1 + (STACK_SIZE + 2) * 2];
				
				int side3s0 = allsides[0] - newSides0;
				int side3s1 = allsides[1] - newSides1;
				int side3s2 = allsides[2] - newSides2;

				weight += 
					F(stack[top], stack[topminus1 + (STACK_SIZE + 2)], side3s2) +
					F(stack[top], stack[topminus1 + (STACK_SIZE + 2) * 2], side3s1) +
					F(stack[top + (STACK_SIZE + 2)], stack[topminus1], side3s2) +
					F(stack[top + (STACK_SIZE + 2)], stack[topminus1 + (STACK_SIZE + 2) * 2], side3s0) +
					F(stack[top + (STACK_SIZE + 2) * 2], stack[topminus1], side3s1) +
					F(stack[top + (STACK_SIZE + 2) * 2], stack[topminus1 + (STACK_SIZE + 2)], side3s0);
					
				stack[topminus1] = newSides0;
				stack[topminus1 + (STACK_SIZE + 2)] = newSides1;
				stack[topminus1 + (STACK_SIZE + 2) * 2] = newSides2;
				
			}
			else { //for polytomies
			
				int nzc[3];
				nzc[0] = nzc[1] = nzc[2] = 0;
				int newSides[3];
				newSides[0] = newSides[1] = newSides[2] = 0;
				
				for(int side = 0; side < 3; side++) {
					for(int i = top - 1; i >= top + geneTreesAsInts4Ints.x; i--) {
						if(stack[i + side * (STACK_SIZE + 2)] > 0) {
							newSides[side] += stack[i + side * (STACK_SIZE + 2)];
							overlap[nzc[side]+ side * (STACK_SIZE + 1)] = stack[i + side * (STACK_SIZE + 2)];
							overlapind[nzc[side] + side * (STACK_SIZE + 1)] = i;
							nzc[side]++;
						}
					}
					
					stack[top + side * (STACK_SIZE + 2)] = allsides[side] - newSides[side];
					
					if(stack[top + side * (STACK_SIZE + 2)] > 0) {
						overlap[nzc[side] + side * (STACK_SIZE + 1)] = stack[top + side * (STACK_SIZE + 2)];
						overlapind[nzc[side] + side * (STACK_SIZE + 1)] = top;
						nzc[side]++;					
					}
					stack[top + geneTreesAsInts4Ints.x + side * (STACK_SIZE + 2)] = newSides[side];
				}
				
				for(int i = nzc[0] - 1; i >= 0; i--) {
					for(int j = nzc[1] - 1; j >= 0; j--) {
						for(int k = nzc[2] - 1; k >= 0; k--) {
							if(overlapind[i] != overlapind[j + (STACK_SIZE + 1)] && overlapind[i] != overlapind[k + (STACK_SIZE + 1) * 2] && overlapind[j + (STACK_SIZE + 1)] != overlapind[k + (STACK_SIZE + 1) * 2])
								weight += F(overlap[i], overlap[j + (STACK_SIZE + 1)], overlap[k + (STACK_SIZE + 1) * 2]);
						}
					}
				}
				
				top = top + geneTreesAsInts4Ints.x + 1;
				
			}
		}
		else {
			break;
		}
		if(counter < geneTreesAsIntsLength) {
			counter++;
			if (newTree) {
				newTree = 0;

				allsides[0] = bitIntersectionSize(&allArray[treeCounter * SPECIES_WORD_LENGTH], trip.cluster1);
				allsides[1] = bitIntersectionSize(&allArray[treeCounter * SPECIES_WORD_LENGTH], trip.cluster2);
				allsides[2] = bitIntersectionSize(&allArray[treeCounter * SPECIES_WORD_LENGTH], trip.cluster3);

				treeCounter++;

			}
			if (geneTreesAsInts4Ints.y >= 0) {
				stack[top] = ((trip.cluster1[SPECIES_WORD_LENGTH - 1 - geneTreesAsInts4Ints.y / LONG_BIT_LENGTH])>>(geneTreesAsInts4Ints.y % LONG_BIT_LENGTH)) & 1;
				stack[top + (STACK_SIZE + 2)] = ((trip.cluster2[SPECIES_WORD_LENGTH - 1 - geneTreesAsInts4Ints.y / LONG_BIT_LENGTH])>>(geneTreesAsInts4Ints.y % LONG_BIT_LENGTH)) & 1;
				stack[top + (STACK_SIZE + 2) * 2] = ((trip.cluster3[SPECIES_WORD_LENGTH - 1 - geneTreesAsInts4Ints.y / LONG_BIT_LENGTH])>>(geneTreesAsInts4Ints.y % LONG_BIT_LENGTH)) & 1;
				top++;
			}
			else if (geneTreesAsInts4Ints.y == INT_MIN) {
				top = 0;
				newTree = 1;
			}
			else if (geneTreesAsInts4Ints.y == -2) {
				top--;
				int topminus1 = top - 1;
				int newSides0 = stack[top] + stack[topminus1];
				int newSides1 = stack[top + (STACK_SIZE + 2)] + stack[topminus1 + (STACK_SIZE + 2)];
				int newSides2 = stack[top + (STACK_SIZE + 2) * 2] + stack[topminus1 + (STACK_SIZE + 2) * 2];
				
				int side3s0 = allsides[0] - newSides0;
				int side3s1 = allsides[1] - newSides1;
				int side3s2 = allsides[2] - newSides2;

				weight += 
					F(stack[top], stack[topminus1 + (STACK_SIZE + 2)], side3s2) +
					F(stack[top], stack[topminus1 + (STACK_SIZE + 2) * 2], side3s1) +
					F(stack[top + (STACK_SIZE + 2)], stack[topminus1], side3s2) +
					F(stack[top + (STACK_SIZE + 2)], stack[topminus1 + (STACK_SIZE + 2) * 2], side3s0) +
					F(stack[top + (STACK_SIZE + 2) * 2], stack[topminus1], side3s1) +
					F(stack[top + (STACK_SIZE + 2) * 2], stack[topminus1 + (STACK_SIZE + 2)], side3s0);
					
				stack[topminus1] = newSides0;
				stack[topminus1 + (STACK_SIZE + 2)] = newSides1;
				stack[topminus1 + (STACK_SIZE + 2) * 2] = newSides2;
				
			}
			else { //for polytomies
			
				int nzc[3];
				nzc[0] = nzc[1] = nzc[2] = 0;
				int newSides[3];
				newSides[0] = newSides[1] = newSides[2] = 0;
				
				for(int side = 0; side < 3; side++) {
					for(int i = top - 1; i >= top + geneTreesAsInts4Ints.y; i--) {
						if(stack[i + side * (STACK_SIZE + 2)] > 0) {
							newSides[side] += stack[i + side * (STACK_SIZE + 2)];
							overlap[nzc[side]+ side * (STACK_SIZE + 1)] = stack[i + side * (STACK_SIZE + 2)];
							overlapind[nzc[side] + side * (STACK_SIZE + 1)] = i;
							nzc[side]++;
						}
					}
					
					stack[top + side * (STACK_SIZE + 2)] = allsides[side] - newSides[side];
					
					if(stack[top + side * (STACK_SIZE + 2)] > 0) {
						overlap[nzc[side] + side * (STACK_SIZE + 1)] = stack[top + side * (STACK_SIZE + 2)];
						overlapind[nzc[side] + side * (STACK_SIZE + 1)] = top;
						nzc[side]++;					
					}
					stack[top + geneTreesAsInts4Ints.y + side * (STACK_SIZE + 2)] = newSides[side];
				}
				
				for(int i = nzc[0] - 1; i >= 0; i--) {
					for(int j = nzc[1] - 1; j >= 0; j--) {
						for(int k = nzc[2] - 1; k >= 0; k--) {
							if(overlapind[i] != overlapind[j + (STACK_SIZE + 1)] && overlapind[i] != overlapind[k + (STACK_SIZE + 1) * 2] && overlapind[j + (STACK_SIZE + 1)] != overlapind[k + (STACK_SIZE + 1) * 2])
								weight += F(overlap[i], overlap[j + (STACK_SIZE + 1)], overlap[k + (STACK_SIZE + 1) * 2]);
						}
					}
				}
				
				top = top + geneTreesAsInts4Ints.y + 1;
				
			}
		}
		else {
			break;
		}
		
		if(counter < geneTreesAsIntsLength) {
			counter++;
			if (newTree) {
				newTree = 0;

				allsides[0] = bitIntersectionSize(&allArray[treeCounter * SPECIES_WORD_LENGTH], trip.cluster1);
				allsides[1] = bitIntersectionSize(&allArray[treeCounter * SPECIES_WORD_LENGTH], trip.cluster2);
				allsides[2] = bitIntersectionSize(&allArray[treeCounter * SPECIES_WORD_LENGTH], trip.cluster3);

				treeCounter++;

			}
			if (geneTreesAsInts4Ints.z >= 0) {
				stack[top] = ((trip.cluster1[SPECIES_WORD_LENGTH - 1 - geneTreesAsInts4Ints.z / LONG_BIT_LENGTH])>>(geneTreesAsInts4Ints.z % LONG_BIT_LENGTH)) & 1;
				stack[top + (STACK_SIZE + 2)] = ((trip.cluster2[SPECIES_WORD_LENGTH - 1 - geneTreesAsInts4Ints.z / LONG_BIT_LENGTH])>>(geneTreesAsInts4Ints.z % LONG_BIT_LENGTH)) & 1;
				stack[top + (STACK_SIZE + 2) * 2] = ((trip.cluster3[SPECIES_WORD_LENGTH - 1 - geneTreesAsInts4Ints.z / LONG_BIT_LENGTH])>>(geneTreesAsInts4Ints.z % LONG_BIT_LENGTH)) & 1;
				top++;
			}
			else if (geneTreesAsInts4Ints.z == INT_MIN) {
				top = 0;
				newTree = 1;
			}
			else if (geneTreesAsInts4Ints.z == -2) {
				top--;
				int topminus1 = top - 1;
				int newSides0 = stack[top] + stack[topminus1];
				int newSides1 = stack[top + (STACK_SIZE + 2)] + stack[topminus1 + (STACK_SIZE + 2)];
				int newSides2 = stack[top + (STACK_SIZE + 2) * 2] + stack[topminus1 + (STACK_SIZE + 2) * 2];
				
				int side3s0 = allsides[0] - newSides0;
				int side3s1 = allsides[1] - newSides1;
				int side3s2 = allsides[2] - newSides2;

				weight += 
					F(stack[top], stack[topminus1 + (STACK_SIZE + 2)], side3s2) +
					F(stack[top], stack[topminus1 + (STACK_SIZE + 2) * 2], side3s1) +
					F(stack[top + (STACK_SIZE + 2)], stack[topminus1], side3s2) +
					F(stack[top + (STACK_SIZE + 2)], stack[topminus1 + (STACK_SIZE + 2) * 2], side3s0) +
					F(stack[top + (STACK_SIZE + 2) * 2], stack[topminus1], side3s1) +
					F(stack[top + (STACK_SIZE + 2) * 2], stack[topminus1 + (STACK_SIZE + 2)], side3s0);
					
				stack[topminus1] = newSides0;
				stack[topminus1 + (STACK_SIZE + 2)] = newSides1;
				stack[topminus1 + (STACK_SIZE + 2) * 2] = newSides2;
				
			}
			else { //for polytomies
			
				int nzc[3];
				nzc[0] = nzc[1] = nzc[2] = 0;
				int newSides[3];
				newSides[0] = newSides[1] = newSides[2] = 0;
				
				for(int side = 0; side < 3; side++) {
					for(int i = top - 1; i >= top + geneTreesAsInts4Ints.z; i--) {
						if(stack[i + side * (STACK_SIZE + 2)] > 0) {
							newSides[side] += stack[i + side * (STACK_SIZE + 2)];
							overlap[nzc[side]+ side * (STACK_SIZE + 1)] = stack[i + side * (STACK_SIZE + 2)];
							overlapind[nzc[side] + side * (STACK_SIZE + 1)] = i;
							nzc[side]++;
						}
					}
					
					stack[top + side * (STACK_SIZE + 2)] = allsides[side] - newSides[side];
					
					if(stack[top + side * (STACK_SIZE + 2)] > 0) {
						overlap[nzc[side] + side * (STACK_SIZE + 1)] = stack[top + side * (STACK_SIZE + 2)];
						overlapind[nzc[side] + side * (STACK_SIZE + 1)] = top;
						nzc[side]++;					
					}
					stack[top + geneTreesAsInts4Ints.z + side * (STACK_SIZE + 2)] = newSides[side];
				}
				
				for(int i = nzc[0] - 1; i >= 0; i--) {
					for(int j = nzc[1] - 1; j >= 0; j--) {
						for(int k = nzc[2] - 1; k >= 0; k--) {
							if(overlapind[i] != overlapind[j + (STACK_SIZE + 1)] && overlapind[i] != overlapind[k + (STACK_SIZE + 1) * 2] && overlapind[j + (STACK_SIZE + 1)] != overlapind[k + (STACK_SIZE + 1) * 2])
								weight += F(overlap[i], overlap[j + (STACK_SIZE + 1)], overlap[k + (STACK_SIZE + 1) * 2]);
						}
					}
				}
				
				top = top + geneTreesAsInts4Ints.z + 1;
				
			}
		}
		else {
			break;
		}
		
		if(counter < geneTreesAsIntsLength) {
			counter++;
			if (newTree) {
				newTree = 0;

				allsides[0] = bitIntersectionSize(&allArray[treeCounter * SPECIES_WORD_LENGTH], trip.cluster1);
				allsides[1] = bitIntersectionSize(&allArray[treeCounter * SPECIES_WORD_LENGTH], trip.cluster2);
				allsides[2] = bitIntersectionSize(&allArray[treeCounter * SPECIES_WORD_LENGTH], trip.cluster3);

				treeCounter++;

			}
			if (geneTreesAsInts4Ints.w >= 0) {
				stack[top] = ((trip.cluster1[SPECIES_WORD_LENGTH - 1 - geneTreesAsInts4Ints.w / LONG_BIT_LENGTH])>>(geneTreesAsInts4Ints.w % LONG_BIT_LENGTH)) & 1;
				stack[top + (STACK_SIZE + 2)] = ((trip.cluster2[SPECIES_WORD_LENGTH - 1 - geneTreesAsInts4Ints.w / LONG_BIT_LENGTH])>>(geneTreesAsInts4Ints.w % LONG_BIT_LENGTH)) & 1;
				stack[top + (STACK_SIZE + 2) * 2] = ((trip.cluster3[SPECIES_WORD_LENGTH - 1 - geneTreesAsInts4Ints.w / LONG_BIT_LENGTH])>>(geneTreesAsInts4Ints.w % LONG_BIT_LENGTH)) & 1;
				top++;
			}
			else if (geneTreesAsInts4Ints.w == INT_MIN) {
				top = 0;
				newTree = 1;
			}
			else if (geneTreesAsInts4Ints.w == -2) {
				top--;
				int topminus1 = top - 1;
				int newSides0 = stack[top] + stack[topminus1];
				int newSides1 = stack[top + (STACK_SIZE + 2)] + stack[topminus1 + (STACK_SIZE + 2)];
				int newSides2 = stack[top + (STACK_SIZE + 2) * 2] + stack[topminus1 + (STACK_SIZE + 2) * 2];
				
				int side3s0 = allsides[0] - newSides0;
				int side3s1 = allsides[1] - newSides1;
				int side3s2 = allsides[2] - newSides2;

				weight += 
					F(stack[top], stack[topminus1 + (STACK_SIZE + 2)], side3s2) +
					F(stack[top], stack[topminus1 + (STACK_SIZE + 2) * 2], side3s1) +
					F(stack[top + (STACK_SIZE + 2)], stack[topminus1], side3s2) +
					F(stack[top + (STACK_SIZE + 2)], stack[topminus1 + (STACK_SIZE + 2) * 2], side3s0) +
					F(stack[top + (STACK_SIZE + 2) * 2], stack[topminus1], side3s1) +
					F(stack[top + (STACK_SIZE + 2) * 2], stack[topminus1 + (STACK_SIZE + 2)], side3s0);
					
				stack[topminus1] = newSides0;
				stack[topminus1 + (STACK_SIZE + 2)] = newSides1;
				stack[topminus1 + (STACK_SIZE + 2) * 2] = newSides2;
				
			}
			else { //for polytomies
			
				int nzc[3];
				nzc[0] = nzc[1] = nzc[2] = 0;
				int newSides[3];
				newSides[0] = newSides[1] = newSides[2] = 0;
				
				for(int side = 0; side < 3; side++) {
					for(int i = top - 1; i >= top + geneTreesAsInts4Ints.w; i--) {
						if(stack[i + side * (STACK_SIZE + 2)] > 0) {
							newSides[side] += stack[i + side * (STACK_SIZE + 2)];
							overlap[nzc[side]+ side * (STACK_SIZE + 1)] = stack[i + side * (STACK_SIZE + 2)];
							overlapind[nzc[side] + side * (STACK_SIZE + 1)] = i;
							nzc[side]++;
						}
					}
					
					stack[top + side * (STACK_SIZE + 2)] = allsides[side] - newSides[side];
					
					if(stack[top + side * (STACK_SIZE + 2)] > 0) {
						overlap[nzc[side] + side * (STACK_SIZE + 1)] = stack[top + side * (STACK_SIZE + 2)];
						overlapind[nzc[side] + side * (STACK_SIZE + 1)] = top;
						nzc[side]++;					
					}
					stack[top + geneTreesAsInts4Ints.w + side * (STACK_SIZE + 2)] = newSides[side];
				}
				
				for(int i = nzc[0] - 1; i >= 0; i--) {
					for(int j = nzc[1] - 1; j >= 0; j--) {
						for(int k = nzc[2] - 1; k >= 0; k--) {
							if(overlapind[i] != overlapind[j + (STACK_SIZE + 1)] && overlapind[i] != overlapind[k + (STACK_SIZE + 1) * 2] && overlapind[j + (STACK_SIZE + 1)] != overlapind[k + (STACK_SIZE + 1) * 2])
								weight += F(overlap[i], overlap[j + (STACK_SIZE + 1)], overlap[k + (STACK_SIZE + 1) * 2]);
						}
					}
				}
				
				top = top + geneTreesAsInts4Ints.w + 1;
				
			}
		}
		else {
			break;
		}
	}
	weightArray[idx] = weight;
}
