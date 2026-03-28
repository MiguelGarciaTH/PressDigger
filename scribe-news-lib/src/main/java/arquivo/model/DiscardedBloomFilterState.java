package arquivo.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Persists the raw bytes of a Bloom filter so it survives service restarts.
 * One row per named filter (e.g. "discarded_articles").
 */
@Entity
@Table(name = "discarded_bloom_filter_state")
public class DiscardedBloomFilterState {

    /** Logical name of the filter — used as the primary key. */
    @Id
    @Column(length = 100)
    private String name;

    /** Serialised {@link java.util.BitSet} bytes. */
    @Column(name = "bit_data", columnDefinition = "bytea", nullable = false)
    private byte[] bitData;

    /** Number of bits in the filter (needed to reconstruct it). */
    @Column(nullable = false)
    private int filterSize;

    /** Number of hash functions (needed to reconstruct it). */
    @Column(nullable = false)
    private int numHashFunctions;

    /** Total elements added before this snapshot. */
    @Column(nullable = false)
    private int addedElements;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected DiscardedBloomFilterState() {
    }

    public DiscardedBloomFilterState(String name, byte[] bitData, int filterSize,
                                     int numHashFunctions, int addedElements) {
        this.name = name;
        this.bitData = bitData;
        this.filterSize = filterSize;
        this.numHashFunctions = numHashFunctions;
        this.addedElements = addedElements;
        this.updatedAt = LocalDateTime.now();
    }

    public String getName() { return name; }
    public byte[] getBitData() { return bitData; }
    public int getFilterSize() { return filterSize; }
    public int getNumHashFunctions() { return numHashFunctions; }
    public int getAddedElements() { return addedElements; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public void setBitData(byte[] bitData) { this.bitData = bitData; }
    public void setFilterSize(int filterSize) { this.filterSize = filterSize; }
    public void setNumHashFunctions(int numHashFunctions) { this.numHashFunctions = numHashFunctions; }
    public void setAddedElements(int addedElements) { this.addedElements = addedElements; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}

