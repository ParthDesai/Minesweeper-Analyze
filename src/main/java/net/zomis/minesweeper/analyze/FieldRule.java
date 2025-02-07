package net.zomis.minesweeper.analyze;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * A constraint of a number of fields or {@link FieldGroup}s that should have a specific sum
 * 
 * @author Simon Forsberg
 * @param <T> Field type
 */
public class FieldRule<T> {
	
	private final T cause;
	private final List<FieldGroup<T>> fields;
	private int result = 0;
	
	/**
	 * Create a copy of an existing rule.
	 * 
	 * @param copyFrom Rule to copy
	 */
	public FieldRule(FieldRule<T> copyFrom) {
		this.fields = new ArrayList<FieldGroup<T>>(copyFrom.fields); // Deep copy? Probably not. FieldGroup don't change much.
		this.result = copyFrom.result;
		this.cause = copyFrom.cause;
	}
	
	/**
	 * Create a rule from a list of fields and a result (create a new FieldGroup for it)
	 * 
	 * @param cause The reason for why this rule is added (optional, may be null)
	 * @param rule Fields that this rule applies to
	 * @param result The value that should be forced for the fields
	 */
	public FieldRule(T cause, Collection<T> rule, int result) {
		this.fields = new ArrayList<FieldGroup<T>>();
		this.fields.add(new FieldGroup<T>(rule));
		this.result = result;
		this.cause = cause;
	}
	
	FieldRule(T cause, FieldGroup<T> group, int result) {
		this.cause = cause;
		this.fields = new ArrayList<FieldGroup<T>>();
		this.fields.add(group);
		this.result = result;
	}
	
	boolean checkIntersection(FieldRule<T> rule) {
		if (rule == this)
			return false;
		
		List<FieldGroup<T>> fieldsCopy = new ArrayList<FieldGroup<T>>(fields);
		List<FieldGroup<T>> ruleFieldsCopy = new ArrayList<FieldGroup<T>>(rule.fields);
		
		for (FieldGroup<T> groupA : fieldsCopy) {
			for (FieldGroup<T> groupB : ruleFieldsCopy) {
				if (groupA == groupB) {
					continue;
				}
				
				FieldGroupSplit<T> splitResult = FieldGroupSplit.split(groupA, groupB);
				if (splitResult == null) {
					continue; // nothing to split
				}
				
				FieldGroup<T> both = splitResult.getBoth();
				FieldGroup<T> onlyA = splitResult.getOnlyA();
				FieldGroup<T> onlyB = splitResult.getOnlyB();
				
				this.fields.remove(groupA);
				this.fields.add(both);
				if (!onlyA.isEmpty()) { 
					this.fields.add(onlyA);
				}
				
				rule.fields.remove(groupB);
				rule.fields.add(both);
				if (!onlyB.isEmpty()) { 
					rule.fields.add(onlyB);
				}
				return true;
			}
		}
		return false;
	}
	
	public T getCause() {
		return this.cause;
	}

	public Collection<FieldGroup<T>> getFieldGroups() {
		return new ArrayList<FieldGroup<T>>(this.fields);
	}
	
	public int getFieldsCountInGroups() {
		int fieldsCounter = 0;
		for (FieldGroup<T> group : fields) {
			fieldsCounter += group.size();
		}
		return fieldsCounter;
	}
	
	public int getResult() {
		return this.result;
	}
	
	public FieldGroup<T> getSmallestFieldGroup() {
		if (this.fields.isEmpty())
			return null;
		
		FieldGroup<T> result = this.fields.get(0);
		for (FieldGroup<T> group : this.fields) {
			if (group.size() < result.size()) {
				result = group;
			}
		}
		return result;
	}
	
	public boolean isEmpty () {
		return fields.isEmpty() && result == 0;
	}

	public double nCr() {
		if (this.fields.size() != 1)
			throw new IllegalStateException("Rule has more than one group.");
		return Combinatorics.nCr(this.getFieldsCountInGroups(), this.result);
	}

	public SimplifyResult simplify(Map<FieldGroup<T>, Integer> knownValues) {
		if (this.isEmpty()) {
			return SimplifyResult.NO_EFFECT;
		}
		
		Iterator<FieldGroup<T>> it = fields.iterator();
		int totalCount = 0;
		while (it.hasNext()) {
			FieldGroup<T> group = it.next();
			Integer known = knownValues.get(group);
			if (known != null) {
				it.remove();
				result -= known;
			}
			else totalCount += group.size();
		}
		
		// a + b + c = -2 is not a valid rule.
		if (result < 0) {
			return SimplifyResult.FAILED_NEGATIVE_RESULT;
		}
		
		// a + b = 42 is not a valid rule
		if (result > totalCount) {
			return SimplifyResult.FAILED_TOO_BIG_RESULT;
		}
		
		// (a + b) = 1 or (a + b) = 0 would give a value to the (a + b) group and simplify things.
		if (fields.size() == 1) {
			knownValues.put(fields.get(0), result);
			fields.clear();
			result = 0;
			return SimplifyResult.SIMPLIFIED;
		}
		
		// (a + b) + (c + d) = 0 would give the value 0 to all field groups and simplify things
		if (result == 0) {
			for (FieldGroup<T> field : fields) {
				knownValues.put(field, 0);
			}
			fields.clear();
			result = 0;
			return SimplifyResult.SIMPLIFIED;
		}
		
		// (a + b) + (c + d) = 4 would give the value {Group.SIZE} to all Groups.
		if (totalCount == result) {
			for (FieldGroup<T> field : fields) {
				knownValues.put(field, result * field.size() / totalCount);
			}
			return SimplifyResult.SIMPLIFIED;
		}
		return SimplifyResult.NO_EFFECT;
	}

	@Override
	public String toString() {
		StringBuilder rule = new StringBuilder();
		for (FieldGroup<T> field : this.fields) {
			if (rule.length() > 0) {
				rule.append(" + ");
			}
			rule.append(field.toString());
		}
		rule.append(" = ");
		rule.append(result);
		return rule.toString(); 
	}
}
