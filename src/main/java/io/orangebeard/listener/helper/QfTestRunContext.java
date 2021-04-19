package io.orangebeard.listener.helper;

import lombok.Getter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

public class QfTestRunContext {
    @Getter
    private final UUID testRun;
    private final HashMap<String, UUID> nodes = new HashMap<>();
    private final List<UUID> activeContainers = new ArrayList<>();

    public int ok = 0;
    public int nok = 0;
    public int skipped = 0;
    public int notImplemented = 0;

    public QfTestRunContext(UUID testRun) {
        this.testRun = testRun;
    }

    public boolean hasNode(String nodeId) {
        return nodes.containsKey(nodeId);
    }

    public UUID getNode(String nodeId) {
        return nodes.getOrDefault(nodeId, null);
    }

    public void addNode(String id, UUID node) {
        nodes.put(id, node);
    }

    public void removeNode(String id) {
        nodes.remove(id);
    }

    public void setCurrentContainer(UUID suite) {
        activeContainers.add(suite);
    }

    public void endActiveContainer() {
        activeContainers.remove(getActiveContainer());
    }

    public UUID getActiveContainer() {
        try {
            return activeContainers.get(activeContainers.size() - 1);
        } catch (IndexOutOfBoundsException e) {
            return null;
        }
    }
}
