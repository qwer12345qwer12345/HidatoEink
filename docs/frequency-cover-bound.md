# Frequency coverage bound

For m nonempty known difference sets, if one clue appears in at most F sets, every hitting set contains at least ceil(m/F) clues. The optimizer takes the maximum of this lower bound and the existing disjoint-set packing lower bound. The bound never certifies uniqueness alone. A triangle of sets {2,3}, {3,4}, {2,4} improves the bound from one to two clues. Exhaustive tests compare the combined bound with the true minimum for 100 deterministic small random set families.
