/**
 * Copyright (c) 2015 Lemur Consulting Ltd.
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package uk.co.flax.biosolr;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.apache.solr.common.util.NamedList;
import org.apache.solr.common.util.SimpleOrderedMap;

/**
 * POJO representing an entry in the hierarchical facet tree.
 *
 * <p>Implements Comparable, so that entries in the tree may be ordered by their
 * value count.</p>
 */
public class TreeFacetField implements Comparable<TreeFacetField>, Serializable {

	private static final long serialVersionUID = 5709339278691781478L;

	private static final String LABEL_KEY = "label";
	private static final String VALUE_KEY = "value";
	private static final String COUNT_KEY = "count";
	private static final String TOTAL_KEY = "total";
	private static final String HIERARCHY_KEY = "hierarchy";

	private final String label;
	private final String value;
	private final long count;
	private final long childCount;
	private final Set<TreeFacetField> hierarchy;

	/**
	 * Construct a new TreeFacetField node.
	 * @param label the label for the node (optional).
	 * @param value the facet value.
	 * @param count the actual facet count for this node.
	 * @param childCount the total count for facets which are children of this
	 * node.
	 * @param hierarchy the set of nodes which comprise the children of this node.
	 */
	public TreeFacetField(String label, String value, long count, long childCount, Set<TreeFacetField> hierarchy) {
		this.label = label;
		this.value = value;
		this.count = count;
		this.childCount = childCount;
		this.hierarchy = hierarchy;
	}

	public String getValue() {
		return value;
	}

	public long getCount() {
		return count;
	}

	public long getChildCount() {
		return childCount;
	}

	public long getTotal() {
		return count + childCount;
	}

	public Set<TreeFacetField> getHierarchy() {
		return hierarchy;
	}

	@Override
	public int compareTo(TreeFacetField o) {
		int ret = 0;

		if (o == null) {
			ret = 1;
		} else {
			ret = (int) (getTotal() - o.getTotal());
			if (ret == 0) {
				// If the counts are the same, compare the ID as well, to double-check
				// whether they're actually the same entry
				ret = getValue().compareTo(o.getValue());
			}
		}

		return ret;
	}

	/**
	 * Convert this object to a SimpleOrderedMap, making it easier to serialize.
	 * @return the equivalent SimpleOrderedMap for this object.
	 */
	public SimpleOrderedMap<Object> toMap() {
		SimpleOrderedMap<Object> map = new SimpleOrderedMap<>();
		
		if (label != null) {
			map.add(LABEL_KEY, label);
		}
		map.add(VALUE_KEY, value);
		map.add(COUNT_KEY, count);
		map.add(TOTAL_KEY, getTotal());
		if (hierarchy != null && hierarchy.size() > 0) {
			// Recurse through the child nodes, converting each to a map
			List<NamedList<Object>> hierarchyList = new ArrayList<>(hierarchy.size());
			for (TreeFacetField tff : hierarchy) {
				hierarchyList.add(tff.toMap());
			}
			map.add(HIERARCHY_KEY, hierarchyList);
		}
		
		return map;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (int) (count ^ (count >>> 32));
		result = prime * result + ((value == null) ? 0 : value.hashCode());
		result = prime * result + ((label == null) ? 0 : label.hashCode());
		result = prime * result + (int) (childCount ^ (childCount >>> 32));
		return result;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (!(obj instanceof TreeFacetField)) {
			return false;
		}
		TreeFacetField other = (TreeFacetField) obj;
		if (count != other.count) {
			return false;
		}
		if (value == null) {
			if (other.value != null) {
				return false;
			}
		} else if (!value.equals(other.value)) {
			return false;
		}
		if (label == null) {
			if (other.label != null) {
				return false;
			}
		} else if (!label.equals(other.label)) {
			return false;
		}
		if (childCount != other.childCount) {
			return false;
		}
		return true;
	}

}