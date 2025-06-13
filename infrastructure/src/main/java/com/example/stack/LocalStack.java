package com.example.stack;

import software.amazon.awscdk.*;
import software.amazon.awscdk.services.ec2.*;
import software.amazon.awscdk.services.ec2.InstanceType;
import software.amazon.awscdk.services.ecs.*;
import software.amazon.awscdk.services.ecs.Protocol;
import software.amazon.awscdk.services.ecs.patterns.ApplicationLoadBalancedFargateService;
import software.amazon.awscdk.services.logs.LogGroup;
import software.amazon.awscdk.services.logs.RetentionDays;
import software.amazon.awscdk.services.rds.*;
import software.amazon.awscdk.services.route53.CfnHealthCheck;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class LocalStack extends Stack {

    private final Vpc vpc;
    private final Cluster ecsCluster;

    public LocalStack(final App scope,
                      final String id,
                      final StackProps props) {
        super(scope, id, props);

        this.vpc = createVpc();

        DatabaseInstance authServiceDB = createDatabaseInstance("AuthServiceDB", "auth-service-db");
        DatabaseInstance patientServiceDB = createDatabaseInstance("PatientServiceDB", "patient-service-db");

        CfnHealthCheck authDBHealthCheck = createHealthCheck(authServiceDB, "AuthServiceDBHealthCheck");
        CfnHealthCheck patientDBHealthCheck = createHealthCheck(patientServiceDB, "PatientServiceDBHealthCheck");

        this.ecsCluster = createEcsCluster();

        FargateService authService = createFargateService("AuthService", "auth-service",
                List.of(4005), authServiceDB,
                Map.of("JWT_SECRET", "caf3b50e92f786a75509fd079596f04990f9f964ad31272511d22aebd05db089ef621d2f5baf41b7423d5a556c2753da382669e5f2e6ac465685643418021f2de1e7fb00822c846ba7fac6cb599d24fac359fc2210be2b5b67832fef553aa56f42be83c59eaa7549d4798b5bdbfb7c59c69676c769ae93c85cd7037af2cedbf542b84e5002c618aa9a28b5343b47dea63bc0828dce61a4a3e8378df6da88088375affc4876294450ff87d8e952e4dc2750f0fc001c8fcec1baaf97f64dc37dc5f09b0261a0f41ce6ef9ee1525a08aab3740a9eec56c841deaa95d3ae53a018e3594bbdac29ce8c77db9be4118ff680a6a57b0d1be94049a726ff219811a1a2f1"));

        authService.getNode().addDependency(authDBHealthCheck);
        authService.getNode().addDependency(authServiceDB);

        FargateService billingService = createFargateService("BillingService", "billing-service",
                List.of(4001, 9001), null, null);

        FargateService analyticsService = createFargateService("AnalyticsService", "analytics-service",
                List.of(4002), null, null);

        FargateService patientService = createFargateService("PatientService", "patient-service",
                List.of(4000), patientServiceDB,
                Map.of("BILLING_SERVICE_ADDRESS", "host.docker.internal",
                        "BILLING_SERVICE_GRPC_PORT", "9001"));

        patientService.getNode().addDependency(patientServiceDB);
        patientService.getNode().addDependency(patientDBHealthCheck);
        patientService.getNode().addDependency(billingService);

        createApiGatewayService();
    }

    private Vpc createVpc() {
        return Vpc.Builder.create(this, "PatientManagementVPC")
                .vpcName("PatientManagementVPC")
                .maxAzs(2)
                .build();
    }

    private DatabaseInstance createDatabaseInstance(String id, String dbName) {
        return DatabaseInstance.Builder.create(this, id)
                .engine(DatabaseInstanceEngine.postgres(PostgresInstanceEngineProps.builder()
                        .version(PostgresEngineVersion.VER_17_2).build()))
                .vpc(vpc)
                .instanceType(InstanceType.of(InstanceClass.BURSTABLE2, InstanceSize.MICRO))
                .allocatedStorage(20)
                .credentials(Credentials.fromGeneratedSecret("admin_user"))
                .databaseName(dbName)
                .removalPolicy(RemovalPolicy.DESTROY)
                .build();
    }

    private CfnHealthCheck createHealthCheck(DatabaseInstance db, String id) {
        return CfnHealthCheck.Builder.create(this, id)
                .healthCheckConfig(CfnHealthCheck.HealthCheckConfigProperty.builder()
                        .type("TCP")
                        .port(Token.asNumber(db.getDbInstanceEndpointPort()))
                        .ipAddress(db.getDbInstanceEndpointAddress())
                        .requestInterval(30)
                        .failureThreshold(3)
                        .build())
                .build();
    }

    private Cluster createEcsCluster() {
        return Cluster.Builder.create(this, "PatientManagementCluster")
                .vpc(vpc)
                .defaultCloudMapNamespace(CloudMapNamespaceOptions.builder()
                        .name("patient-management.local")
                        .build())
                .build();
    }

    private FargateService createFargateService(String id,
                                                String imageName,
                                                List<Integer> ports,
                                                DatabaseInstance db,
                                                Map<String, String> additionalEnvVars) {

        FargateTaskDefinition taskDefinition = FargateTaskDefinition.Builder
                .create(this, id + "Task")
                .cpu(256)
                .memoryLimitMiB(512)
                .build();

        ContainerDefinitionOptions.Builder containerOptions =
                ContainerDefinitionOptions.builder()
                        .image(ContainerImage.fromRegistry(imageName))
                        .portMappings(ports.stream()
                                .map(port -> PortMapping.builder()
                                        .containerPort(port)
                                        .hostPort(port)
                                        .protocol(Protocol.TCP)
                                        .build())
                                .toList())
                        .logging(LogDriver.awsLogs(AwsLogDriverProps.builder()
                                .logGroup(LogGroup.Builder.create(this, id + "LogGroup")
                                        .logGroupName("/ecs/" + imageName)
                                        .removalPolicy(RemovalPolicy.DESTROY)
                                        .retention(RetentionDays.ONE_DAY)
                                        .build())
                                .streamPrefix(imageName)
                                .build()));

        Map<String, String> envVars = new HashMap<>();
        envVars.put("SPRING_KAFKA_BOOTSTRAP_SERVERS", "host.docker.internal:9092");

        if (additionalEnvVars != null) {
            envVars.putAll(additionalEnvVars);
        }

        if( db != null) {
            envVars.put("SPRING_DATASOURCE_URL", "jdbc:postgresql://%s:%s/%s-db"
                    .formatted(
                            db.getDbInstanceEndpointAddress(),
                            db.getDbInstanceEndpointPort(),
                            imageName
                    ));
            envVars.put("SPRING_DATASOURCE_USERNAME", "admin_user");
            envVars.put("SPRING_DATASOURCE_PASSWORD",
                    db.getSecret().secretValueFromJson("password").toString());
            envVars.put("SPRING_JPA_HIBERNATE_DDL_AUTO", "update");
            envVars.put("SPRING_SQL_INIT_MODE", "always");
            envVars.put("SPRING_DATASOURCE_HIKARI_INITIALIZATION_FAIL_TIMEOUT", "60000");
        }

        containerOptions.environment(envVars);

        taskDefinition.addContainer(imageName + "Container", containerOptions.build());

        return FargateService.Builder.create(this, id)
                .cluster(ecsCluster)
                .taskDefinition(taskDefinition)
                .assignPublicIp(false)
                .serviceName(imageName)
                .build();
    }

    private void createApiGatewayService(){
        FargateTaskDefinition taskDefinition = FargateTaskDefinition.Builder
                .create(this, "APIGatewayTaskDefinition")
                .cpu(256)
                .memoryLimitMiB(512)
                .build();

        ContainerDefinitionOptions containerOptions =
                ContainerDefinitionOptions.builder()
                        .image(ContainerImage.fromRegistry("api-gateway"))
                        .environment(Map.of(
                                "SPRING_PROFILES_ACTIVE", "prod",
                                "AUTH_SERVICE_URL","http://host.docker.internal:4005"
                        ))
                        .portMappings(List.of(4004).stream()
                                .map(port -> PortMapping.builder()
                                        .containerPort(port)
                                        .hostPort(port)
                                        .protocol(Protocol.TCP)
                                        .build())
                                .toList())
                        .logging(LogDriver.awsLogs(AwsLogDriverProps.builder()
                                .logGroup(LogGroup.Builder.create(this,  "ApiGatewayLogGroup")
                                        .logGroupName("/ecs/api-gateway")
                                        .removalPolicy(RemovalPolicy.DESTROY)
                                        .retention(RetentionDays.ONE_DAY)
                                        .build())
                                .streamPrefix("api-gateway")
                                .build()))
                        .build();

        taskDefinition.addContainer("APIGatewayContainer", containerOptions);

        ApplicationLoadBalancedFargateService apiGateway
                = ApplicationLoadBalancedFargateService.Builder.create(this, "APIGatewayService")
                .cluster(ecsCluster)
                .serviceName("api-gateway")
                .taskDefinition(taskDefinition)
                .desiredCount(1)
                .healthCheckGracePeriod(Duration.seconds(60))
                .build();
    }

    public static void main(final String[] args) {
        App app = new App(AppProps.builder().outdir("./cdk.out").build());

        StackProps props = StackProps.builder()
                .synthesizer(new BootstraplessSynthesizer())
                .build();

        new LocalStack(app, "localstack", props);
        app.synth();

        System.out.println("App synthesizing in complete...");
    }
}
