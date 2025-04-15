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

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class Main {
    // Number of legitimate users
    private static final int LEGITIMATE_USERS = 5;
    // Number of attack sources (DDoS attackers)
    private static final int ATTACK_SOURCES = 1;
    // Number of requests per attacker
    private static final int REQUESTS_PER_ATTACKER = 10;
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
        // Initialize the CloudSim Plus simulation
        CloudSimPlus simulation = new CloudSimPlus();

        // Enable logging events for DDoS analysis
        // simulation.terminateAt(100);

        // Create a broker
        DatacenterBroker broker = new DatacenterBrokerSimple(simulation);

        // Create VMs
        List<Vm> vmList = createVms(2); // Create 2 VMs to serve requests

        // Create all cloudlets (both legitimate and attack requests)
        List<RequestDetails> allRequests = createCloudlets();

        // NEW CODE: Filter requests using AI model
        List<Cloudlet> filteredRequests = filterMaliciousRequests(allRequests);

        // Submit VMs and filtered cloudlets to the broker
        broker.submitVmList(vmList);
        broker.submitCloudletList(filteredRequests);

        // Add a listener to track resource utilization during the DDoS attack
        // simulation.addOnClockTickListener(Main::monitorResources);

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
        private final boolean isAttack;

        public RequestDetails(int id, Cloudlet cloudlet, int sourceId, boolean isAttack) {
            this.id = id;
            this.cloudlet = cloudlet;
            this.sourceId = sourceId;
            this.isAttack = isAttack;
        }

        public int getId() { return id; }
        public Cloudlet getCloudlet() { return cloudlet; }
        public int getSourceId() { return sourceId; }
        public boolean isAttack() { return isAttack; }
    }

    private static List<RequestDetails> createCloudlets() {
        List<RequestDetails> requestList = new ArrayList<>();
        int cloudletId = 0;

        // Create legitimate user requests (will have lower IDs)
        for (int i = 0; i < LEGITIMATE_USERS; i++) {
            Cloudlet cloudlet = new CloudletSimple(cloudletId, LEGITIMATE_LENGTH, LEGITIMATE_PES);
            cloudlet.setUtilizationModelCpu(new UtilizationModelFull());
            cloudlet.setUtilizationModelRam(new UtilizationModelDynamic(0.2));
            cloudlet.setUtilizationModelBw(new UtilizationModelDynamic(0.1));

            // Create request details with metadata
            RequestDetails request = new RequestDetails(
                    cloudletId,
                    cloudlet,
                    i,  // sourceId (user ID)
                    false // not an attack
            );

            requestList.add(request);
            cloudletId++;
        }

        // Create a massive amount of attack requests (DDoS)
        Random random = new Random(42); // Fixed seed for reproducibility
        for (int i = 0; i < ATTACK_SOURCES; i++) {
            for (int j = 0; j < REQUESTS_PER_ATTACKER; j++) {
                // Small variation in attack request size to simulate real attack patterns
                int length = ATTACK_LENGTH + random.nextInt(1000);
                Cloudlet cloudlet = new CloudletSimple(cloudletId, length, ATTACK_PES);
                cloudlet.setUtilizationModelCpu(new UtilizationModelFull());
                cloudlet.setUtilizationModelRam(new UtilizationModelDynamic(0.1));
                cloudlet.setUtilizationModelBw(new UtilizationModelDynamic(0.1));

                // Add listener to detect when attack requests start executing
                int attackerId = i;
                cloudlet.addOnStartListener(event ->
                        onAttackRequestStarted(event, attackerId));

                // Create request details with metadata
                RequestDetails request = new RequestDetails(
                        cloudletId,
                        cloudlet,
                        i + LEGITIMATE_USERS,  // sourceId (attacker ID)
                        true  // is an attack
                );

                requestList.add(request);
                cloudletId++;
            }
        }

        return requestList;
    }

    private static void onAttackRequestStarted(CloudletVmEventInfo info, int attackerId) {
        if (info.getTime() % 50 == 0) { // Reduce logging frequency
            System.out.printf("Time %.2f: Attack request %d from attacker %d started execution on VM %d\n",
                    info.getTime(), info.getCloudlet().getId(), attackerId, info.getVm().getId());
        }
    }

    private static List<Cloudlet> filterMaliciousRequests(List<RequestDetails> allRequests) {
        List<Cloudlet> filteredCloudlets = new ArrayList<>();
        int blockedRequests = 0;

        for (RequestDetails request : allRequests) {
            boolean isMalicious = detectMaliciousRequest(request);

            if (!isMalicious) {
                filteredCloudlets.add(request.getCloudlet());
            } else {
                blockedRequests++;
            }
        }

        System.out.println("AI filter blocked " + blockedRequests + " potentially malicious requests");
        return filteredCloudlets;
    }

    // detection implementation
    private static boolean detectMaliciousRequest(RequestDetails request) {
        // can use patterns like:
        // 1. Request pattern (frequency from same source)
        // 2. Request size and resource demands
        // 3. Historical behavior patterns
        // 4. Content analysis of request

        // For demonstration, we are using the sourceId
        // hardcoded right now
        int id = request.getId();
        int sourceId = request.getSourceId();
        Cloudlet cloudlet = request.getCloudlet();

        System.out.println("Analyzing request - ID: " + id +
                ", Source: " + sourceId +
                ", Cloudlet length: " + cloudlet.getLength());

        // todo: detection logic
        return sourceId >= LEGITIMATE_USERS;
    }
}