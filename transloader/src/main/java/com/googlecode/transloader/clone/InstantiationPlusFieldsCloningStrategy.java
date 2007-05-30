package com.googlecode.transloader.clone;

import java.lang.reflect.Array;
import java.util.Map;

import com.googlecode.transloader.TransloaderClass;
import com.googlecode.transloader.clone.RecursiveReferenceTraverser.Traversal;

public class InstantiationPlusFieldsCloningStrategy implements CloningStrategy {
	private final RecursiveReferenceTraverser recursiveReferenceTraverser = new RecursiveReferenceTraverser();
	private final CloningStrategy fallbackCloner = new SerializationCloningStrategy();

	private CloningDecisionStrategy cloningDecider;
	private InstantiationStrategy instantiator;

	public InstantiationPlusFieldsCloningStrategy(CloningDecisionStrategy cloningDecider, InstantiationStrategy instantiator) {
		this.cloningDecider = cloningDecider;
		this.instantiator = instantiator;
	}

	public Object cloneObjectToClassLoader(final Object original, final ClassLoader targetClassLoader) throws Exception {
		Traversal cloningTraversal = new Traversal() {
			public Object traverse(Object currentObject, Map referenceHistory) throws Exception {
				return InstantiationPlusFieldsCloningStrategy.this.clone(currentObject, targetClassLoader, referenceHistory);
			}
		};
		return recursiveReferenceTraverser.performWithoutFollowingCircles(cloningTraversal, original);
	}

	private Object clone(Object original, ClassLoader targetClassLoader, Map cloneHistory) throws Exception {
		if (original == null) return null;
		try {
			return performInstantiationCloning(original, targetClassLoader, cloneHistory);
		} catch (Exception e) {
			return performFallbackCloning(original, targetClassLoader);
		}
	}

	private Object performInstantiationCloning(Object original, ClassLoader targetClassLoader, Map cloneHistory)
			throws Exception {
		Object clone = original;
		if (cloningDecider.shouldCloneObjectItself(original, targetClassLoader))
			clone = instantiateClone(original, targetClassLoader);
		cloneHistory.put(original, clone);
		if (cloningDecider.shouldCloneObjectContent(original, targetClassLoader))
			cloneContent(original, clone, targetClassLoader);
		return clone;
	}

	private Object instantiateClone(Object original, ClassLoader targetClassLoader) throws Exception {
		Class originalClass = original.getClass();
		if (originalClass.isArray()) {
			Class originalComponentType = originalClass.getComponentType();
			Class cloneComponentType = new TransloaderClass(originalComponentType).getEquivalentFrom(targetClassLoader);
			return Array.newInstance(cloneComponentType, Array.getLength(original));
		}
		Class cloneClass = new TransloaderClass(originalClass).getEquivalentFrom(targetClassLoader);
		return instantiator.newInstance(cloneClass);
	}

	private void cloneContent(Object original, Object clone, ClassLoader targetClassLoader) throws Exception {
		if (original.getClass().isArray()) {
			cloneArrayComponents(original, clone, targetClassLoader);
		} else {
			cloneInstanceFields(original, clone, targetClassLoader);
		}
	}

	private void cloneArrayComponents(Object original, Object clone, ClassLoader targetClassLoader) throws Exception {
		for (int i = 0; i < Array.getLength(original); i++) {
			Object originalComponent = Array.get(original, i);
			Object cloneComponent = cloneObjectToClassLoader(originalComponent, targetClassLoader);
			Array.set(clone, i, cloneComponent);
		}
	}

	private void cloneInstanceFields(Object original, Object clone, ClassLoader targetClassLoader) throws Exception {
		FieldReflector originalReflector = new FieldReflector(original, getClassLoader(original));
		FieldReflector cloneReflector = new FieldReflector(clone, targetClassLoader);
		FieldDescription[] fieldDescriptions = originalReflector.getAllInstanceFieldDescriptions();
		for (int i = 0; i < fieldDescriptions.length; i++) {
			FieldDescription description = fieldDescriptions[i];
			Object originalFieldValue = originalReflector.getValue(description);
			Object cloneFieldValue = cloneObjectToClassLoader(originalFieldValue, targetClassLoader);
			cloneReflector.setValue(description, cloneFieldValue);
		}
	}

	private Object performFallbackCloning(Object original, ClassLoader targetClassLoader) throws Exception {
		return fallbackCloner.cloneObjectToClassLoader(original, targetClassLoader);
	}

	private static ClassLoader getClassLoader(Object original) {
		ClassLoader classLoader = original.getClass().getClassLoader();
		return classLoader == null ? ClassLoader.getSystemClassLoader() : classLoader;
	}
}