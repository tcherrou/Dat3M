package com.dat3m.dartagnan.program.memory;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;

import java.util.*;

public class Memory {

    private final Map<Address, Location> map;
    private final Map<String, Location> locationIndex;
    private final Map<String, List<Address>> arrays;

    private int nextIndex = 1;

    public Memory() {
        map = new HashMap<>();
        locationIndex = new HashMap<>();
        arrays = new HashMap<>();
    }

    public Location getLocationForAddress(Address address) {
        return map.get(address);
    }

    public List<Address> malloc(String name, int size) {
    	Preconditions.checkArgument(!arrays.containsKey(name), "Illegal malloc. Array " + name + " is already defined");
    	Preconditions.checkArgument(size > 0, "Illegal malloc. Size must be positive");

    	List<Address> addresses = new ArrayList<>();
    	for(int i = 0; i < size; i++) {
    		addresses.add(new Address(nextIndex++));
    	}
    	arrays.put(name, addresses);
    	return addresses;
    }

    public Location getOrCreateLocation(String name){
        if(!locationIndex.containsKey(name)) {
            Location location = new Location(name, new Address(nextIndex++));
            map.put(location.getAddress(), location);
            locationIndex.put(name, location);
            return location;
        }
        return locationIndex.get(name);
    }

    public ImmutableSet<Address> getAllAddresses() {
        Set<Address> result = new HashSet<>(map.keySet());
        for(List<Address> array : arrays.values()){
            result.addAll(array);
        }
        return ImmutableSet.copyOf(result);
    }

    public boolean isArrayPointer(Address address) {
        return arrays.values().stream().anyMatch(array -> array.contains(address));
    }
    
    public Collection<List<Address>> getArrays() {
        return arrays.values();
    }
    
    public List<Address> getArrayFromPointer(Address address) {
        return arrays.values().stream()
                .filter(array -> array.contains(address))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Provided address does not belong to any array."));
    }
}