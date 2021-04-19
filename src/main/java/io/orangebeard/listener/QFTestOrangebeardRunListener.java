package io.orangebeard.listener;


import de.qfs.apps.qftest.extensions.qftest.TestRunEvent;
import de.qfs.apps.qftest.extensions.qftest.TestRunListener;
import de.qfs.apps.qftest.extensions.qftest.TestSuiteNode;
import de.qfs.apps.qftest.step.AbstractComponentDependant;
import io.orangebeard.client.OrangebeardClient;
import io.orangebeard.client.OrangebeardProperties;
import io.orangebeard.client.OrangebeardV1Client;
import io.orangebeard.client.entity.Attribute;
import io.orangebeard.client.entity.FinishTestItem;
import io.orangebeard.client.entity.FinishTestRun;
import io.orangebeard.client.entity.Log;
import io.orangebeard.client.entity.LogLevel;
import io.orangebeard.client.entity.StartTestItem;
import io.orangebeard.client.entity.StartTestRun;
import io.orangebeard.client.entity.Status;
import io.orangebeard.client.entity.TestItemType;
import io.orangebeard.client.entity.UpdateTestRun;
import io.orangebeard.listener.helper.QfTestRunContext;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.HashSet;
import java.util.UUID;

import static de.qfs.apps.qftest.extensions.qftest.TestRunEvent.STATE_ERROR;
import static de.qfs.apps.qftest.extensions.qftest.TestRunEvent.STATE_EXCEPTION;
import static de.qfs.apps.qftest.extensions.qftest.TestRunEvent.STATE_NOT_IMPLEMENTED;
import static de.qfs.apps.qftest.extensions.qftest.TestRunEvent.STATE_OK;
import static de.qfs.apps.qftest.extensions.qftest.TestRunEvent.STATE_RUN_ABORTED;
import static de.qfs.apps.qftest.extensions.qftest.TestRunEvent.STATE_RUN_TERMINATED;
import static de.qfs.apps.qftest.extensions.qftest.TestRunEvent.STATE_SKIPPED;
import static de.qfs.apps.qftest.extensions.qftest.TestRunEvent.STATE_WARNING;


public class QFTestOrangebeardRunListener implements TestRunListener {

    private final OrangebeardClient client;
    private final QfTestRunContext context;


    public QFTestOrangebeardRunListener() {
        OrangebeardProperties orangebeardProperties = new OrangebeardProperties();
        orangebeardProperties.checkPropertiesArePresent();
        client = new OrangebeardV1Client(
                orangebeardProperties.getEndpoint(),
                orangebeardProperties.getAccessToken(),
                orangebeardProperties.getProjectName(),
                orangebeardProperties.requiredValuesArePresent()
        );

        StartTestRun startRun = StartTestRun.builder()
                .name(orangebeardProperties.getTestSetName())
                .description(orangebeardProperties.getDescription())
                .startTime(LocalDateTime.now())
                .attributes(new HashSet<>()).build();

        context = new QfTestRunContext(client.startTestRun(startRun));
    }

    @Override
    public void runStarted(TestRunEvent testRunEvent) {
        //Orangebeard run starts on instantiation
    }

    @Override
    public void runStopped(TestRunEvent testRunEvent) {
        while (context.getActiveContainer() != null) {
            FinishTestItem forceFinish = new FinishTestItem(
                    context.getTestRun(),
                    Status.STOPPED,
                    "Unexpected Abort!",
                    new HashSet<>());
            client.finishTestItem(context.getActiveContainer(), forceFinish);
        }

        Status runState = determineItemStatus(testRunEvent);
        if (runState == Status.PASSED) {
            if (context.nok > 0) {
                runState = Status.FAILED;
            }
        }
        FinishTestRun finishRun = new FinishTestRun(runState);
        client.finishTestRun(context.getTestRun(), finishRun);
    }

    @Override
    public void nodeEntered(TestRunEvent testRunEvent) {
        startItem(testRunEvent);
    }

    @Override
    public void nodeExited(TestRunEvent testRunEvent) {
        if (testRunEvent.getNode().getType().equalsIgnoreCase("RootStep")) {
            client.updateTestRun(context.getTestRun(), new UpdateTestRun(testRunEvent.getNode().getComment(), null));
            return;
        }
        String nodeId = testRunEvent.getNode().getId();

        //Check if node is started (allows for runtime listener registration)
        if (!context.hasNode(nodeId)) {
            startItem(testRunEvent);
        }

        updateContextCounters(testRunEvent);
        finishItem(testRunEvent);

    }

    @Override
    public void problemOccurred(TestRunEvent testRunEvent) {
        String nodeId = testRunEvent.getNode().getId();
        if (!context.hasNode(nodeId)) {
            startItem(testRunEvent);
        }
        LogLevel level;
        switch (testRunEvent.getLocalState()) {
            case 1:
                level = LogLevel.warn;
                break;
            case 2:
            case 3:
                level = LogLevel.error;
                break;
            default:
                level = LogLevel.info;
        }
        Log logItem = new Log(context.getTestRun(), context.getNode(nodeId), level, testRunEvent.getMessage());
        client.log(logItem);
    }

    private void startItem(TestRunEvent testRunEvent) {
        TestSuiteNode node = testRunEvent.getNode();
        TestItemType type = determineType(testRunEvent);
        String itemName = node.getName() != null ? node.getName() : testRunEvent.getNode().getStep().toString();
        if (node.getStep() instanceof AbstractComponentDependant) {
            itemName = itemName + " [" + ((AbstractComponentDependant) node.getStep()).getComponentId() + "]";
        }
        StartTestItem startItem = new StartTestItem(
                context.getTestRun(),
                itemName,
                type,
                node.getComment(),
                Collections.singleton(new Attribute("Type", node.getType())));

        UUID startedItem = client.startTestItem(context.getActiveContainer(), startItem);
        if (type != TestItemType.STEP) {
            context.setCurrentContainer(startedItem);
        }
        context.addNode(node.getId(), startedItem);
    }

    private void finishItem(TestRunEvent testRunEvent) {
        Status status = determineItemStatus(testRunEvent);
        TestSuiteNode node = testRunEvent.getNode();

        TestItemType type = determineType(testRunEvent);

        FinishTestItem finishItem = new FinishTestItem(
                context.getTestRun(),
                LocalDateTime.ofInstant(Instant.ofEpochMilli(testRunEvent.getTimestamp()), ZoneId.systemDefault()),
                status,
                node.getComment(),
                new HashSet<>());
        client.finishTestItem(context.getNode(node.getId()), finishItem);

        if (type != TestItemType.STEP) {
            context.endActiveContainer();
        }
        //context.removeNode(node.getId());
    }

    private Status determineItemStatus(TestRunEvent testRunEvent) {
        int state = testRunEvent.getState();
        int localState = testRunEvent.getLocalState();

        if (localState == STATE_SKIPPED || localState == STATE_NOT_IMPLEMENTED) {
            return Status.SKIPPED;
        }

        if (state == STATE_OK || state == STATE_WARNING) {
            if (testRunEvent.getExceptions() == 0 && testRunEvent.getErrors() == 0) {
                return Status.PASSED;
            } else return Status.FAILED;
        }

        if (state == STATE_EXCEPTION || state == STATE_ERROR) {
            return Status.FAILED;
        }

        if (state == STATE_RUN_TERMINATED) {
            if (localState == STATE_OK) {
                return Status.PASSED;
            }
            return Status.FAILED;
        }

        if (state == STATE_RUN_ABORTED) {
            return Status.STOPPED;
        }

        return Status.IN_PROGRESS;
    }

    private TestItemType determineType(TestRunEvent testRunEvent) {
        switch (testRunEvent.getNode().getType()) {
            case "TestSet":
            case "TestSuite":
            case "DataDriver":
                return TestItemType.SUITE;
            case "TestCase":
            case "TestStep":
            case "IfSequence":
            case "ProcedureCall":
            case "Procedure":
            case "DependencyReference":
                return TestItemType.TEST;
            case "SetupSequence":
                return TestItemType.BEFORE_METHOD;
            default:
                return TestItemType.STEP;
        }
    }

    private void updateContextCounters(TestRunEvent testRunEvent) {
        if (testRunEvent.getNode().getType().equalsIgnoreCase("TestCase")) {
            int state = testRunEvent.getState();
            int localState = testRunEvent.getLocalState();
            if (localState == STATE_SKIPPED) {
                context.skipped++;
            } else if (localState == STATE_NOT_IMPLEMENTED) {
                context.notImplemented++;
            } else if (state == STATE_OK || state == STATE_WARNING) {
                context.ok++;
            } else if (state == STATE_EXCEPTION || state == STATE_ERROR) {
                context.nok++;
            }
        }
    }
}
