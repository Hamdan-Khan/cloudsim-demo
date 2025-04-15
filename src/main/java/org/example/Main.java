package org.example;

import org.cloudsimplus.brokers.DatacenterBroker;
import org.cloudsimplus.brokers.DatacenterBrokerSimple;
import org.cloudsimplus.builders.tables.CloudletsTableBuilder;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.cloudlets.CloudletSimple;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.core.Simulation;
import org.cloudsimplus.datacenters.Datacenter;
import org.cloudsimplus.datacenters.DatacenterSimple;
import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.hosts.HostSimple;
import org.cloudsimplus.listeners.EventInfo;
import org.cloudsimplus.resources.Pe;
import org.cloudsimplus.resources.PeSimple;
import org.cloudsimplus.utilizationmodels.UtilizationModelDynamic;
import org.cloudsimplus.utilizationmodels.UtilizationModelFull;
import org.cloudsimplus.vms.Vm;
import org.cloudsimplus.vms.VmSimple;
import org.cloudsimplus.listeners.CloudletVmEventInfo;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

public class Main {
    // Number of legitimate users
    private static final int LEGITIMATE_USERS = 5;
    // Number of attack sources (DDoS attackers)
    private static final int ATTACK_SOURCES = 10;
    // Number of requests per attacker
    private static final int REQUESTS_PER_ATTACKER = 50;
    // Total number of cloudlets (requests)
    private static final int TOTAL_CLOUDLETS = LEGITIMATE_USERS + (ATTACK_SOURCES * REQUESTS_PER_ATTACKER);
    private static Datacenter datacenter;


    // Resource requirements
    private static final int HOST_PES = 8;
    private static final int HOST_MIPS = 1000;
    private static final int HOST_RAM = 20000;
    private static final int HOST_BW = 100000;
    private static final int HOST_STORAGE = 1000000;

    private static final int VM_PES = 4;
    private static final int VM_MIPS = 1000;
    private static final int VM_RAM = 1024;
    private static final int VM_BW = 1000;
    private static final int VM_SIZE = 10000;

    // Legitimate request size
    private static final int LEGITIMATE_LENGTH = 10000;
    private static final int LEGITIMATE_PES = 1;

    // Attack request size (can be small but numerous)
    private static final int ATTACK_LENGTH = 2000;
    private static final int ATTACK_PES = 1;

    public static void main(String[] args) {
        // Generate training data if needed
        // generateTrainingData(2000);

        // Train model
        // WekaTrainer trainer = new WekaTrainer();
        // trainer.trainModelWithWeka();

        // Initialize the CloudSim Plus simulation
        CloudSimPlus simulation = new CloudSimPlus();

        // Create a broker
        DatacenterBroker broker = new DatacenterBrokerSimple(simulation);

        // Create VMs
        List<Vm> vmList = createVms(2); // Create 2 VMs to serve requests

        // Create all cloudlets (both legitimate and attack requests)
        List<RequestDetails> allRequests = createCloudlets(simulation);

        // Weka-based ML detector
        WekaDetector detector = new WekaDetector();

        // Filter requests using the detector
        List<Cloudlet> filteredRequests = filterMaliciousRequests(allRequests, detector, simulation);

        // Submit VMs and filtered cloudlets to the broker
        broker.submitVmList(vmList);
        broker.submitCloudletList(filteredRequests);

        // Create Datacenter with monitoring
        Datacenter datacenter = createDatacenter(simulation);

        // Start the simulation
        System.out.println("Starting DDoS attack simulation with " + LEGITIMATE_USERS + " legitimate users and "
                + ATTACK_SOURCES + " attackers sending " + REQUESTS_PER_ATTACKER + " requests each.");
        System.out.println("Total requests: " + TOTAL_CLOUDLETS);
        simulation.start();

        // Print results
        List<Cloudlet> finishedCloudlets = broker.getCloudletFinishedList();
        System.out.println("\nFinished requests: " + finishedCloudlets.size() + " out of " + TOTAL_CLOUDLETS);

        // Calculate statistics for both legitimate and attack requests
        calculateStatistics(finishedCloudlets);

        // Output detailed cloudlet information (limit to first 20 to avoid excessive output)
        int outputLimit = Math.min(finishedCloudlets.size(), 20);
        new CloudletsTableBuilder(finishedCloudlets.subList(0, outputLimit)).build();
    }

    private static void calculateStatistics(List<Cloudlet> cloudlets) {
        double avgExecTimeAll = 0;
        double avgExecTimeLegitimate = 0;
        int legitimateFinished = 0;
        int attackFinished = 0;

        for (Cloudlet cloudlet : cloudlets) {
            avgExecTimeAll += cloudlet.getFinishTime() - cloudlet.getExecStartTime();

            // The first LEGITIMATE_USERS cloudlets are legitimate requests
            if (cloudlet.getId() < LEGITIMATE_USERS) {
                avgExecTimeLegitimate += cloudlet.getFinishTime() - cloudlet.getExecStartTime();
                legitimateFinished++;
            } else {
                attackFinished++;
            }
        }

        avgExecTimeAll /= cloudlets.size();
        avgExecTimeLegitimate = legitimateFinished > 0 ? avgExecTimeLegitimate / legitimateFinished : 0;

        System.out.println("\n====== DDoS ATTACK SIMULATION RESULTS ======");
        System.out.println("Legitimate requests completed: " + legitimateFinished + " out of " + LEGITIMATE_USERS);
        System.out.println("Attack requests completed: " + attackFinished + " out of " + (ATTACK_SOURCES * REQUESTS_PER_ATTACKER));
        System.out.println("Average execution time for all requests: " + String.format("%.2f", avgExecTimeAll));
        System.out.println("Average execution time for legitimate requests: " + String.format("%.2f", avgExecTimeLegitimate));
        System.out.println("===========================================");
    }

    private static void monitorResources(EventInfo info) {
        double time = info.getTime();

        if (time % 10 == 0) { // Log every 10 time units
            List<Host> hostList = datacenter.getHostList();

            double totalCpuUsage = 0;
            double totalRamUsage = 0;

            for (Host host : hostList) {
                totalCpuUsage += host.getCpuPercentUtilization() * 100;
                totalRamUsage += host.getRam().getPercentUtilization() * 100;
            }

            totalCpuUsage /= hostList.size();
            totalRamUsage /= hostList.size();

            System.out.printf("\nTime %.2f: Average Host CPU Usage = %.2f%%, RAM Usage = %.2f%%\n",
                    time, totalCpuUsage, totalRamUsage);
        }
    }

    private static Datacenter createDatacenter(CloudSimPlus simulation) {
        List<Host> hostList = new ArrayList<>();

        // Create a list of Processing Elements (PEs or CPU cores)
        List<Pe> peList = new ArrayList<>();
        for (int i = 0; i < HOST_PES; i++) {
            peList.add(new PeSimple(HOST_MIPS));
        }

        // Create 2 hosts to handle the load
        for (int i = 0; i < 2; i++) {
            Host host = new HostSimple(HOST_RAM, HOST_BW, HOST_STORAGE, peList);
            hostList.add(host);
        }

        return new DatacenterSimple(simulation, hostList);
    }

    private static List<Vm> createVms(int count) {
        List<Vm> vmList = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Vm vm = new VmSimple(VM_MIPS, VM_PES);
            vm.setRam(VM_RAM).setBw(VM_BW).setSize(VM_SIZE);
            vmList.add(vm);
        }
        return vmList;
    }

    public static class RequestDetails {
        private final int id;
        private final Cloudlet cloudlet;
        private final int sourceId;
        private final double timestamp;  // When the request was received
        private final String requestPath; // Simulated URL/endpoint path
        private final Map<String, String> headers; // Request headers
        private final int payloadSize;   // Size of request payload
        private final String sessionId;  // null for new sessions
        private final boolean hasValidCredentials; // Based on auth check
        private final boolean isAttack;

        public RequestDetails(int id, Cloudlet cloudlet, int sourceId, double timestamp,
                              String requestPath, Map<String, String> headers,
                              int payloadSize, String sessionId, boolean hasValidCredentials, boolean isAttack) {
            this.id = id;
            this.cloudlet = cloudlet;
            this.sourceId = sourceId;
            this.timestamp = timestamp;
            this.requestPath = requestPath;
            this.headers = headers != null ? headers : new HashMap<>();
            this.payloadSize = payloadSize;
            this.sessionId = sessionId;
            this.hasValidCredentials = hasValidCredentials;
            this.isAttack = isAttack;
        }

        // Simple constructor for backward compatibility
        public RequestDetails(int id, Cloudlet cloudlet, int sourceId,boolean isAttack) {
            this(id, cloudlet, sourceId, System.currentTimeMillis() / 1000.0,
                    "/default", new HashMap<>(), (int) cloudlet.getLength(), null, false,isAttack);
        }

        public int getId() { return id; }
        public Cloudlet getCloudlet() { return cloudlet; }
        public int getSourceId() { return sourceId; }
        public double getTimestamp() { return timestamp; }
        public String getRequestPath() { return requestPath; }
        public Map<String, String> getHeaders() { return headers; }
        public int getPayloadSize() { return payloadSize; }
        public String getSessionId() { return sessionId; }
        public boolean hasValidCredentials() { return hasValidCredentials; }
        public boolean isAttack() { return isAttack; }
    }

    private static List<RequestDetails> createCloudlets(CloudSimPlus simulation) {
        List<RequestDetails> requestList = new ArrayList<>();
        int cloudletId = 0;
        double currentTime = simulation.clock();

        // Create legitimate user requests
        for (int i = 0; i < LEGITIMATE_USERS; i++) {
            Cloudlet cloudlet = new CloudletSimple(cloudletId, LEGITIMATE_LENGTH, LEGITIMATE_PES);
            cloudlet.setUtilizationModelCpu(new UtilizationModelFull());
            cloudlet.setUtilizationModelRam(new UtilizationModelDynamic(0.2));
            cloudlet.setUtilizationModelBw(new UtilizationModelDynamic(0.1));

            // Create request with metadata
            RequestDetails request = new RequestDetails(
                    cloudletId,
                    cloudlet,
                    i,  // sourceId
                    currentTime + (i * 0.1),  // timestamps staggered
                    "/api/data",  // typical path
                    createLegitimateHeaders(),
                    1024 + (int)(Math.random() * 1000),  // payload size
                    "session-" + i,  // session ID
                    true , // valid credentials
                    false
            );

            requestList.add(request);
            cloudletId++;
        }

        // Create attack requests
        Random random = new Random(42);
        for (int i = 0; i < ATTACK_SOURCES; i++) {
            for (int j = 0; j < REQUESTS_PER_ATTACKER; j++) {
                int length = ATTACK_LENGTH + random.nextInt(1000);

                Cloudlet cloudlet = new CloudletSimple(cloudletId, length, ATTACK_PES);
                cloudlet.setUtilizationModelCpu(new UtilizationModelFull());
                cloudlet.setUtilizationModelRam(new UtilizationModelDynamic(0.1));
                cloudlet.setUtilizationModelBw(new UtilizationModelDynamic(0.1));

                // Very small time gap between attack requests
                double attackTime = currentTime + (i * 0.01) + (j * 0.001);

                // Create request with attack metadata
                RequestDetails request = new RequestDetails(
                        cloudletId,
                        cloudlet,
                        i + LEGITIMATE_USERS,  // sourceId
                        attackTime,  // timestamps very close together
                        "/api/login",  // common attack target
                        createAttackHeaders(),
                        5000 + random.nextInt(2000),  // large payload
                        null,  // no session
                        false , // no valid credentials
                        false
                );

                requestList.add(request);
                cloudletId++;
            }
        }

        return requestList;
    }

    private static Map<String, String> createLegitimateHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
        headers.put("Accept", "text/html,application/json");
        headers.put("Connection", "keep-alive");
        return headers;
    }

    private static Map<String, String> createAttackHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", "DDoS-Client/1.0");
        headers.put("Connection", "close");
        return headers;
    }

    private static void onAttackRequestStarted(CloudletVmEventInfo info, int attackerId) {
        if (info.getTime() % 50 == 0) { // Reduce logging frequency
            System.out.printf("Time %.2f: Attack request %d from attacker %d started execution on VM %d\n",
                    info.getTime(), info.getCloudlet().getId(), attackerId, info.getVm().getId());
        }
    }

    private static List<Cloudlet> filterMaliciousRequests(
            List<RequestDetails> allRequests,
            Object detector,
            CloudSimPlus simulation) {

        List<Cloudlet> filteredCloudlets = new ArrayList<>();
        int blockedRequests = 0;
        int falsePositives = 0;
        int falseNegatives = 0;
        double currentTime = simulation.clock();

        for (RequestDetails request : allRequests) {
            boolean isMalicious = false;

            // Call the appropriate isAttack method based on detector type
            if (detector instanceof DDoSDetector) {
                isMalicious = ((DDoSDetector) detector).isAttack(request, currentTime);
            } else if (detector instanceof WekaDetector) {
                isMalicious = ((WekaDetector) detector).isAttack(request, currentTime);
            }

            // Get ground truth (for evaluation)
            boolean isActualAttack = request.isAttack();

            // Track false positives and negatives if we know ground truth
            if (isMalicious && !isActualAttack) {
                falsePositives++;
            } else if (!isMalicious && isActualAttack) {
                falseNegatives++;
            }

            if (!isMalicious) {
                filteredCloudlets.add(request.getCloudlet());
            } else {
                blockedRequests++;
            }
        }

        System.out.println("DDoS detector blocked " + blockedRequests + " potentially malicious requests");
        System.out.println("False positives: " + falsePositives + " (legitimate requests blocked)");
        System.out.println("False negatives: " + falseNegatives + " (attack requests allowed)");

        return filteredCloudlets;
    }

    public static void generateTrainingData(int numSamples) {
        // Create file
        try (FileWriter fileWriter = new FileWriter("ddos_training_data.csv");
             PrintWriter writer = new PrintWriter(fileWriter)) {

            // Write header
            writer.println("source_request_rate,system_request_rate,payload_size,cpu_demand,bw_demand,is_attack");

            // Generate legitimate sample data
            Random random = new Random(42);
            for (int i = 0; i < numSamples / 2; i++) {
                double sourceRate = 0.1 + (random.nextDouble() * 2.0); // 0.1-2 req/s
                double systemRate = 10 + (random.nextDouble() * 20.0); // 10-30 req/s
                int payloadSize = 100 + random.nextInt(2000);          // 100-2100 bytes
                double cpuDemand = 0.1 + (random.nextDouble() * 0.5);  // 10-60% CPU
                double bwDemand = 0.05 + (random.nextDouble() * 0.2);  // 5-25% BW

                // Write legitimate sample (label = 0)
                writer.printf("%.2f,%.2f,%d,%.2f,%.2f,0\n",
                        sourceRate, systemRate, payloadSize, cpuDemand, bwDemand);
            }

            // Generate attack sample data
            for (int i = 0; i < numSamples / 2; i++) {
                double sourceRate = 5.0 + (random.nextDouble() * 20.0); // 5-25 req/s
                double systemRate = 30 + (random.nextDouble() * 100.0); // 30-130 req/s
                int payloadSize = 1000 + random.nextInt(10000);         // 1000-11000 bytes
                double cpuDemand = 0.6 + (random.nextDouble() * 0.4);   // 60-100% CPU
                double bwDemand = 0.3 + (random.nextDouble() * 0.7);    // 30-100% BW

                // Write attack sample (label = 1)
                writer.printf("%.2f,%.2f,%d,%.2f,%.2f,1\n",
                        sourceRate, systemRate, payloadSize, cpuDemand, bwDemand);
            }

            System.out.println("Generated " + numSamples + " training samples");

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}