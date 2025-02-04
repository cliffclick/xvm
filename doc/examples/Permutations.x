/**
 * Implements permutations without repetition.
 */
module Permutations
    {
    static Int[][] permut(Int items)
        {
        if (items <= 1)
            {
            // with one item, there is a single permutation; otherwise there are no permutations
            return items == 1 ? [[0]] : [];
            }

        // the "pattern" for all values but the first value in each permutation is
        // derived from the permutations of the next smaller number of items
        Int[][] pattern = permut(items - 1);

        // build the list of all permutations for the specified number of items by iterating only
        // the first digit
        Int[][] result = new Int[][];
        for (Int prefix : 0 ..< items)
            {
            for (Int[] suffix : pattern)
                {
                result.add(new Int[items](i -> i == 0 ? prefix
                                                      : (prefix + suffix[i-1] + 1) % items));
                }
            }
        return result;
        }

    void run()
        {
        @Inject Console console;
        console.print($"permut(3) = {permut(3)}");
        }
    }