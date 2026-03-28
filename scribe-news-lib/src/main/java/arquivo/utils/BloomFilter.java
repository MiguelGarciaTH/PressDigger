package arquivo.utils;

import java.util.BitSet;

public class BloomFilter {

    private final BitSet bitSet;
    private final int size;
    private final int numHashFunctions;
    private int addedElements;

    /**
     * @param expectedInsertions expected number of elements
     * @param falsePositiveRate  desired false positive probability (e.g. 0.01 for 1%)
     */
    public BloomFilter(int expectedInsertions, double falsePositiveRate) {
        this.size = optimalSize(expectedInsertions, falsePositiveRate);
        this.numHashFunctions = optimalHashCount(expectedInsertions, size);
        this.bitSet = new BitSet(size);
        this.addedElements = 0;
    }

    /**
     * Deserialization constructor — reconstructs a filter from persisted state.
     *
     * @param bitData          raw bytes from {@link #toByteArray()}
     * @param size             bit-array length used when the filter was created
     * @param numHashFunctions number of hash functions used when the filter was created
     * @param addedElements    number of elements that were added before serialisation
     */
    public BloomFilter(byte[] bitData, int size, int numHashFunctions, int addedElements) {
        this.size = size;
        this.numHashFunctions = numHashFunctions;
        this.bitSet = BitSet.valueOf(bitData);
        this.addedElements = addedElements;
    }

    public void add(String element) {
        for (int i = 0; i < numHashFunctions; i++) {
            int index = hash(element, i);
            bitSet.set(index);
        }
        addedElements++;
    }

    /**
     * @return true if the element MIGHT exist, false if it DEFINITELY does not exist
     */
    public boolean mightContain(String element) {
        for (int i = 0; i < numHashFunctions; i++) {
            int index = hash(element, i);
            if (!bitSet.get(index)) {
                return false;
            }
        }
        return true;
    }

    public int getAddedElements() {
        return addedElements;
    }

    public byte[] toByteArray() {
        return bitSet.toByteArray();
    }

    public int getSize() {
        return size;
    }

    public int getNumHashFunctions() {
        return numHashFunctions;
    }

    private int hash(String element, int seed) {
        int h = murmurHash(element, seed);
        return Math.abs(h) % size;
    }

    /**
     * Simplified MurmurHash3-inspired hash with a seed.
     */
    private int murmurHash(String element, int seed) {
        int h = seed;
        for (int i = 0; i < element.length(); i++) {
            h ^= element.charAt(i);
            h *= 0x5bd1e995;
            h ^= h >>> 13;
        }
        h ^= element.length();
        h ^= h >>> 16;
        return h;
    }

    // m = -(n * ln(p)) / (ln(2)^2)
    private static int optimalSize(int n, double p) {
        return (int) Math.ceil(-n * Math.log(p) / (Math.log(2) * Math.log(2)));
    }

    // k = (m / n) * ln(2)
    private static int optimalHashCount(int n, int m) {
        return Math.max(1, (int) Math.round((double) m / n * Math.log(2)));
    }
}