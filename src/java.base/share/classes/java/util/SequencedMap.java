/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package java.util;

/**
 * A Map that has a well-defined encounter order and that is reversible.
 * The <i>encounter order</i> of a {@code SequencedMap} is similar to that of the
 * elements of a {@link SequencedCollection}, but the ordering applies to
 * mappings instead of individual elements.
 * <p>
 * The bulk operations on this map, including the {@link #forEach forEach} and the
 * {@link #replaceAll replaceAll} methods, operate on this map's mappings in
 * encounter order.
 * <p>
 * The view collections provided by the
 * {@link #keySet keySet},
 * {@link #values values},
 * {@link #entrySet entrySet},
 * {@link #sequencedKeySet sequencedKeySet},
 * {@link #sequencedValues sequencedValues},
 * and
 * {@link #sequencedEntrySet sequencedEntrySet} methods all reflect the encounter order
 * of this map. Even though the return values of the
 * {@link #keySet keySet},
 * {@link #values values}, and
 * {@link #entrySet entrySet} methods are not sequenced <i>types</i>, the elements
 * in those view collections do reflect the encounter order of this map. Thus, the
 * iterators returned by the statements
 * <pre>{@code
 *     var it1 = sequencedMap.entrySet().iterator();
 *     var it2 = sequencedMap.sequencedEntrySet().iterator();
 * }</pre>
 * both provide the mappings of {@code sequencedMap} in that map's encounter order.
 * <p>
 * {@code SequencedMap} also defines the {@link #reversed} method, which provides a
 * reverse-ordered <a href="Collection.html#view">view</a> of this map.
 * In the reverse-ordered view, the concepts of first and last are inverted, as
 * are the concepts of successor and predecessor. The first mapping of this map
 * is the last mapping of the reverse-ordered view, and vice-versa. The successor of some
 * mapping in this map is its predecessor in the reversed view, and vice-versa. All
 * methods that respect the encounter order of the map operate as if the encounter order
 * is inverted. For instance, the {@link #forEach forEach} method of the reversed view reports
 * the mappings in order from the last mapping of this map to the first. In addition, all of
 * the view collections of the reversed view also reflect the inverse of this map's
 * encounter order. For example,
 * <pre>{@code
 *     var itr = sequencedMap.reversed().entrySet().iterator();
 * }</pre>
 * provides the mappings of this map in the inverse of the encounter order, that is, from
 * the last mapping to the first mapping. The availability of the {@code reversed} method,
 * and its impact on the ordering semantics of all applicable methods and views, allow convenient
 * iteration, searching, copying, and streaming of this map's mappings in either forward order or
 * reverse order.
 * <p>
 * The {@link Map.Entry} instances obtained from the {@link entrySet} view
 * of this collection, and from its reverse-ordered view, maintain a connection
 * to the underlying map. If the underlying map permits it, calling the
 * {@link Map.Entry#setValue setValue} method on such an {@code Entry} will
 * modify the value of the underlying mapping. It is, however, unspecified whether
 * modifications to the value in the underlying mapping are visible in the
 * {@code Entry} instance.
 * <p>
 * Depending upon the underlying implementation, the {@code Entry} instances
 * returned by other methods in this interface might or might not be connected
 * to the underlying map entries. For example, it is not specified by this interface
 * whether the {@code setValue} method of an {@code Entry} obtained from the
 * {@link #firstEntry firstEntry} method will update a mapping in the underlying map, or whether
 * changes to the underlying map are visible in the returned {@code Entry}.
 * <p>
 * This interface has the same requirements on the {@code equals} and {@code hashCode}
 * methods as defined by {@link Map#equals Map.equals} and {@link Map#hashCode Map.hashCode}.
 * Thus, a {@code Map} and a {@code SequencedMap} will compare equals if and only
 * if they have equal mappings, irrespective of ordering.
 * <p>
 * This class is a member of the
 * <a href="{@docRoot}/java.base/java/util/package-summary.html#CollectionsFramework">
 * Java Collections Framework</a>.
 *
 * @param <K> the type of keys maintained by this map
 * @param <V> the type of mapped values
 * @since 20
 */
public interface SequencedMap<K, V> extends Map<K, V> {
    /**
     * Returns a reverse-ordered <a href="Collection.html#view">view</a> of this map.
     * The encounter order of elements in the returned view is the inverse of the encounter
     * order of elements in this map. The reverse ordering affects all order-sensitive operations,
     * including those on the view collections of the returned view. If the implementation permits
     * modifications to this view, the modifications "write through" to the underlying map.
     * Changes to the underlying map might or might not be visible in this reversed view,
     * depending upon the implementation.
     *
     * @return a reverse-ordered view of this map
     */
    SequencedMap<K, V> reversed();

    /**
     * Returns the first key-value mapping in this map,
     * or {@code null} if the map is empty.
     *
     * @implSpec
     * The default implementation in this class is implemented as if:
     * <pre>{@code
     *     var it = entrySet().iterator();
     *     return it.hasNext() ? it.next() : null;
     * }</pre>
     *
     * @return the first key-value mapping,
     *         or {@code null} if this map is empty
     */
    default Map.Entry<K,V> firstEntry() {
        var it = entrySet().iterator();
        return it.hasNext() ? it.next() : null;
    }

    /**
     * Returns the last key-value mapping in this map,
     * or {@code null} if the map is empty.
     *
     * @implSpec
     * The default implementation in this class is implemented as if:
     * <pre>{@code
     *     var it = reversed().entrySet().iterator();
     *     return it.hasNext() ? it.next() : null;
     * }</pre>
     *
     * @return the last key-value mapping,
     *         or {@code null} if this map is empty
     */
    default Map.Entry<K,V> lastEntry() {
        var it = reversed().entrySet().iterator();
        return it.hasNext() ? it.next() : null;
    }

    /**
     * Removes and returns the first key-value mapping in this map,
     * or {@code null} if the map is empty (optional operation).
     *
     * @implSpec
     * The default implementation in this class is implemented as if:
     * <pre>{@code
     *     var it = entrySet().iterator();
     *     if (it.hasNext()) {
     *         var entry = it.next();
     *         it.remove();
     *         return entry;
     *     } else {
     *         return null;
     *     }
     * }</pre>
     *
     * @return the removed first entry of this map,
     *         or {@code null} if this map is empty
     */
    default Map.Entry<K,V> pollFirstEntry() {
        var it = entrySet().iterator();
        if (it.hasNext()) {
            var entry = it.next();
            it.remove();
            return entry;
        } else {
            return null;
        }
    }

    /**
     * Removes and returns the last key-value mapping in this map,
     * or {@code null} if the map is empty (optional operation).
     *
     * @implSpec
     * The default implementation in this class is implemented as if:
     * <pre>{@code
     *     var it = reversed().entrySet().iterator();
     *     if (it.hasNext()) {
     *         var entry = it.next();
     *         it.remove();
     *         return entry;
     *     } else {
     *         return null;
     *     }
     * }</pre>
     *
     * @return the removed last entry of this map,
     *         or {@code null} if this map is empty
     */
    default Map.Entry<K,V> pollLastEntry() {
        var it = reversed().entrySet().iterator();
        if (it.hasNext()) {
            var entry = it.next();
            it.remove();
            return entry;
        } else {
            return null;
        }
    }

    /**
     * Inserts this mapping into the map if it is not already present, or replaces the value
     * of a mapping if it is already present (optional operation).
     * After this operation completes normally, the given mapping will present in this map,
     * and it will be the first mapping in this map's in encounter order.
     *
     * @implSpec The implementation of this method in this class always throws
     * {@code UnsupportedOperationException}.
     *
     * @param k the key
     * @param v the value
     * @return the value previously associated with k, or null if none
     */
    default V putFirst(K k, V v) {
        throw new UnsupportedOperationException();
    }

    /**
     * Inserts this mapping into the map if it is not already present, or replaces the value
     * of a mapping if it is already present (optional operation).
     * After this operation completes normally, the given mapping will present in this map,
     * and it will be the last mapping in this map's in encounter order.
     *
     * @implSpec The implementation of this method in this class always throws
     * {@code UnsupportedOperationException}.
     *
     * @param k the key
     * @param v the value
     * @return the value previously associated with k, or null if none
     */
    default V putLast(K k, V v) {
        throw new UnsupportedOperationException();
    }

    /**
     * Returns a {@link SequencedSet} view of this map's keySet.
     *
     * @implSpec
     * The implementation of this method in this class returns a {@code SequencedSet}
     * implementation that delegates all operations either to this map or to this map's
     * {@link #keySet}, except for its {@link SequencedSet#reversed reversed} method,
     * which instead returns the result of calling {@code sequencedKeySet} on this map's
     * reverse-ordered view.
     *
     * @return a SequencedSet view of this map's keySet
     */
    default SequencedSet<K> sequencedKeySet() {
        class SeqKeySet extends AbstractSet<K> implements SequencedSet<K> {
            public int size() {
                return SequencedMap.this.size();
            }
            public Iterator<K> iterator() {
                return SequencedMap.this.keySet().iterator();
            }
            public SequencedSet<K> reversed() {
                return SequencedMap.this.reversed().sequencedKeySet();
            }
        }
        return new SeqKeySet();
    }

    /**
     * Returns a {@link SequencedCollection} view of this map's values collection.
     *
     * @implSpec
     * The implementation of this method in this class returns a {@code SequencedCollection}
     * implementation that delegates all operations either to this map or to this map's
     * {@link #values} collection, except for its {@link SequencedCollection#reversed reversed}
     * method, which instead returns the result of calling {@code sequencedValues} on this map's
     * reverse-ordered view.
     *
     * @return a SequencedCollection view of this map's values collection
     */
    default SequencedCollection<V> sequencedValues() {
        class SeqValues extends AbstractCollection<V> implements SequencedCollection<V> {
            public int size() {
                return SequencedMap.this.size();
            }
            public Iterator<V> iterator() {
                return SequencedMap.this.values().iterator();
            }
            public SequencedCollection<V> reversed() {
                return SequencedMap.this.reversed().sequencedValues();
            }
        }
        return new SeqValues();
    }

    /**
     * Returns a {@link SequencedSet} view of this map's entrySet.
     *
     * @implSpec
     * The implementation of this method in this class returns a {@code SequencedSet}
     * implementation that delegates all operations either to this map or to this map's
     * {@link #entrySet}, except for its {@link SequencedSet#reversed reversed} method,
     * which instead returns the result of calling {@code sequencedEntrySet} on this map's
     * reverse-ordered view.
     *
     * @return a SequencedSet view of this map's entrySet
     */
    default SequencedSet<Map.Entry<K, V>> sequencedEntrySet() {
        class SeqEntrySet extends AbstractSet<Map.Entry<K, V>>
                implements SequencedSet<Map.Entry<K, V>> {
            public int size() {
                return SequencedMap.this.size();
            }
            public Iterator<Map.Entry<K, V>> iterator() {
                return SequencedMap.this.entrySet().iterator();
            }
            public SequencedSet<Map.Entry<K, V>> reversed() {
                return SequencedMap.this.reversed().sequencedEntrySet();
            }
        }
        return new SeqEntrySet();
    }
}
