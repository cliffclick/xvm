class HashSet<ElementType>
        implements Set<ElementType>
    {
    construct()
        {
        assert(ElementType instanceof Hashable);

        Hasher<ElementType> hasher = new NaturalHasher<Hashable+ElementType>();
        }

    construct(Hasher<ElementType> hasher)
        {
        this.hasher = hasher;
        }

    public/private Hasher<ElementType> hasher;

    private class Entry<ElementType>(ElementType value, Entry? next);

    private Entry<ElementType>?[] buckets; // TODO

    @Override
    public/private Int size;

    @Override
    Boolean contains(ElementType value)
        {
        Int nHash   = hasher.hashOf(value);
        Int nBucket = nHash % buckets.size;

        Entry<ElementType>? entry = buckets[nBucket];
        while (entry != null)
            {
            if (hasher.areEqual(value, entry.value))
                {
                return true;
                }
            entry = entry.next;
            }

        return false;
        }

    // ...
    }
