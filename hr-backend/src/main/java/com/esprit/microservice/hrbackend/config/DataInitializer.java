package com.esprit.microservice.hrbackend.config;

import com.esprit.microservice.hrbackend.entity.*;
import com.esprit.microservice.hrbackend.repository.DepartmentRepository;
import com.esprit.microservice.hrbackend.repository.DocumentRepository;
import com.esprit.microservice.hrbackend.repository.EmployeeRepository;
import com.esprit.microservice.hrbackend.repository.LeaveRepository;
import com.esprit.microservice.hrbackend.repository.TrainingEnrollmentRepository;
import com.esprit.microservice.hrbackend.repository.TrainingRepository;
import com.esprit.microservice.hrbackend.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final DepartmentRepository departmentRepository;
    private final EmployeeRepository employeeRepository;
    private final DocumentRepository documentRepository;
    private final TrainingRepository trainingRepository;
    private final TrainingEnrollmentRepository trainingEnrollmentRepository;
    private final LeaveRepository leaveRepository;
    private final FileStorageService fileStorageService;

    @Override
    public void run(String... args) throws Exception {
        // Seed departments/employees if empty
        if (departmentRepository.count() == 0) {
            seedData();
        }
        // Seed sample leaves if empty
        if (leaveRepository.count() == 0) {
            seedLeaves();
        }
    }

    private void seedData() {
        // 1. Create Departments
        Map<String, Department> depts = new HashMap<>();

        Department it = Department.builder()
                .name("IT")
                .description("Développement logiciel, infrastructures réseaux, support informatique et projets IA.")
                .budget(750000.0)
                .build();
        depts.put("IT", departmentRepository.save(it));

        Department rh = Department.builder()
                .name("RH")
                .description("Gestion des talents, paie, recrutement, formations et bien-être des collaborateurs.")
                .budget(180000.0)
                .build();
        depts.put("RH", departmentRepository.save(rh));

        Department marketing = Department.builder()
                .name("Marketing")
                .description("Communication externe, image de marque, réseaux sociaux et stratégies de croissance.")
                .budget(320000.0)
                .build();
        depts.put("Marketing", departmentRepository.save(marketing));

        Department finance = Department.builder()
                .name("Finance")
                .description("Comptabilité générale, facturation, contrôle budgétaire et planification financière.")
                .budget(450000.0)
                .build();
        depts.put("Finance", departmentRepository.save(finance));

        // 2. Create Employees
        Employee managerIt = Employee.builder()
                .firstName("Thomas")
                .lastName("Rousseau")
                .email("manager@corp.com")
                .phone("+33 6 12 34 56 78")
                .position("Directeur Technique")
                .address("42 Rue de la Paix, 75002 Paris")
                .hireDate(LocalDate.of(2020, 2, 10))
                .status(Status.ACTIVE)
                .salary(65000.0)
                .availableLeaveDays(28)
                .department(depts.get("IT"))
                .build();
        employeeRepository.save(managerIt);

        Employee managerRh = Employee.builder()
                .firstName("Marc")
                .lastName("Dubois")
                .email("rh@corp.com")
                .phone("+33 6 87 65 43 21")
                .position("Responsable RH")
                .address("14 Avenue des Champs-Élysées, 75008 Paris")
                .hireDate(LocalDate.of(2021, 5, 12))
                .status(Status.ACTIVE)
                .salary(55000.0)
                .availableLeaveDays(25)
                .department(depts.get("RH"))
                .build();
        employeeRepository.save(managerRh);

        Employee adminEmp = Employee.builder()
                .firstName("Marc")
                .lastName("Dubois")
                .email("admin@corp.com")
                .phone("+33 6 12 34 56 78")
                .position("Directeur Général & Administrateur")
                .address("10 Rue de la Paix, 75002 Paris")
                .hireDate(LocalDate.of(2020, 1, 15))
                .status(Status.ACTIVE)
                .salary(75000.0)
                .availableLeaveDays(30)
                .department(depts.get("RH"))
                .build();
        employeeRepository.save(adminEmp);

        Employee employeeIt1 = Employee.builder()
                .firstName("Sophie")
                .lastName("Martin")
                .email("employee@corp.com")
                .phone("+33 6 11 22 33 44")
                .position("Développeuse Fullstack")
                .address("75 Rue de Rennes, 75006 Paris")
                .hireDate(LocalDate.of(2023, 3, 15))
                .status(Status.ACTIVE)
                .salary(42000.0)
                .availableLeaveDays(22)
                .department(depts.get("IT"))
                .manager(managerIt)
                .build();
        employeeRepository.save(employeeIt1);

        Employee employeeIt2 = Employee.builder()
                .firstName("Ahmed")
                .lastName("Alami")
                .email("ahmed.alami@corp.com")
                .phone("+212 6 00 11 22 33")
                .position("Développeur Java")
                .address("10 Boulevard Anfa, Casablanca")
                .hireDate(LocalDate.of(2024, 1, 15))
                .status(Status.ACTIVE)
                .salary(38000.0)
                .availableLeaveDays(25)
                .department(depts.get("IT"))
                .manager(managerIt)
                .build();
        employeeRepository.save(employeeIt2);

        // Link department managers
        it.setManager(managerIt);
        departmentRepository.save(it);

        rh.setManager(managerRh);
        departmentRepository.save(rh);

        // 3. Seed documents
        byte[] dummyPdfBytes = "%PDF-1.4 ... Dummy PDF Content".getBytes();

        String path1 = fileStorageService.storeBytes(dummyPdfBytes, "Contrat_Travail_Ahmed_Alami.pdf", "documents");
        Document contratAhmed = Document.builder()
                .name("Contrat_Travail_Ahmed_Alami.pdf")
                .type("Contrat")
                .contentType("application/pdf")
                .size((long) dummyPdfBytes.length)
                .uploadDate(LocalDate.of(2024, 1, 15))
                .filePath(path1)
                .employee(employeeIt2)
                .build();
        documentRepository.save(contratAhmed);

        String path2 = fileStorageService.storeBytes(dummyPdfBytes, "Fiche_Paie_Avril_2026_Ahmed.pdf", "documents");
        Document fichePaieAhmed = Document.builder()
                .name("Fiche_Paie_Avril_2026_Ahmed.pdf")
                .type("Fiche de paie")
                .contentType("application/pdf")
                .size((long) dummyPdfBytes.length)
                .uploadDate(LocalDate.of(2026, 4, 30))
                .filePath(path2)
                .employee(employeeIt2)
                .build();
        documentRepository.save(fichePaieAhmed);

        String path3 = fileStorageService.storeBytes(dummyPdfBytes, "Contrat_Travail_Sophie_Martin.pdf", "documents");
        Document contratSophie = Document.builder()
                .name("Contrat_Travail_Sophie_Martin.pdf")
                .type("Contrat")
                .contentType("application/pdf")
                .size((long) dummyPdfBytes.length)
                .uploadDate(LocalDate.of(2023, 3, 15))
                .filePath(path3)
                .employee(employeeIt1)
                .build();
        documentRepository.save(contratSophie);

        String path4 = fileStorageService.storeBytes(dummyPdfBytes, "Diplome_Master_IT_Sophie.pdf", "documents");
        Document diplomeSophie = Document.builder()
                .name("Diplome_Master_IT_Sophie.pdf")
                .type("Diplôme")
                .contentType("application/pdf")
                .size((long) dummyPdfBytes.length)
                .uploadDate(LocalDate.of(2023, 1, 20))
                .filePath(path4)
                .employee(employeeIt1)
                .build();
        documentRepository.save(diplomeSophie);

        // 4. Seed training catalog
        Training angular = Training.builder()
                .title("Angular 20 & RxJS")
                .description("Maîtriser les composants standalone, le State Management réactif et les architectures complexes avec Angular.")
                .trainer("Thomas Rousseau")
                .startDate(LocalDate.of(2026, 9, 1))
                .duration("5 jours")
                .location("Paris / Distanciel")
                .syllabus("1. Introduction aux Standalone Components\n2. Gestion reactive avec RxJS\n3. State Management et Signaux\n4. Securite OAuth2/OIDC\n5. Final Project")
                .build();
        trainingRepository.save(angular);

        Training spring = Training.builder()
                .title("Java & Spring Boot microservices")
                .description("Concevoir des architectures distribuées de microservices sécurisés avec Spring Boot et Hibernate.")
                .trainer("Sophie Martin")
                .startDate(LocalDate.of(2026, 10, 5))
                .duration("4 jours")
                .location("Distanciel")
                .syllabus("1. Spring Boot & JPA Core\n2. Spring Cloud Config & Service Discovery\n3. API Gateway & Routing\n4. Spring Security OAuth2\n5. Deploying on Kubernetes")
                .build();
        trainingRepository.save(spring);

        Training docker = Training.builder()
                .title("Docker Conteneurisation")
                .description("Comprendre et concevoir des conteneurs isolés pour le déploiement reproductible de vos applications.")
                .trainer("Jean Dupont")
                .startDate(LocalDate.of(2026, 9, 15))
                .duration("2 jours")
                .location("Paris La Defense")
                .syllabus("1. Docker Fundamentals\n2. Writing optimized Dockerfiles\n3. Docker Compose multi-containers\n4. Volumes & Network mappings\n5. CI/CD integration")
                .build();
        trainingRepository.save(docker);

        Training kubernetes = Training.builder()
                .title("Kubernetes Orchestration")
                .description("Pilotez le déploiement, la mise à l'échelle et la maintenance de grappes de conteneurs en production.")
                .trainer("Thomas Rousseau")
                .startDate(LocalDate.of(2026, 11, 10))
                .duration("3 jours")
                .location("Distanciel")
                .syllabus("1. Kubernetes Architecture\n2. Pods, Deployments & Services\n3. ConfigMaps & Secrets\n4. Persistent Volumes\n5. Helm charts")
                .build();
        trainingRepository.save(kubernetes);

        // 5. Seed training enrollments
        TrainingEnrollment enroll1 = TrainingEnrollment.builder()
                .employee(employeeIt1) // Sophie Martin
                .training(angular)
                .enrollmentDate(LocalDate.of(2026, 8, 1))
                .status("En cours")
                .progression(75)
                .build();
        trainingEnrollmentRepository.save(enroll1);

        TrainingEnrollment enroll2 = TrainingEnrollment.builder()
                .employee(employeeIt2) // Ahmed Alami
                .training(spring)
                .enrollmentDate(LocalDate.of(2026, 8, 2))
                .status("En cours")
                .progression(45)
                .build();
        trainingEnrollmentRepository.save(enroll2);
    }

    private void seedLeaves() {
        var employees = employeeRepository.findAll();
        if (employees.isEmpty()) return;

        Long emp1Id = employees.get(0).getId(); // Rousseau Thomas
        Long emp2Id = employees.size() > 1 ? employees.get(1).getId() : emp1Id; // Dubois Marc
        Long emp3Id = employees.size() > 2 ? employees.get(2).getId() : emp1Id; // Martin Sophie
        Long emp4Id = employees.size() > 3 ? employees.get(3).getId() : emp1Id; // Alami Ahmed

        LocalDate now = LocalDate.now();

        // 1. Congé Payé Approuvé (Emp 3: Sophie)
        Leave l1 = Leave.builder()
                .employeeId(emp3Id)
                .startDate(now.withDayOfMonth(5))
                .endDate(now.withDayOfMonth(10))
                .type(LeaveType.ANNUAL)
                .status(LeaveStatus.APPROVED)
                .reason("Vacances d'été")
                .createdAt(LocalDateTime.now().minusDays(10))
                .build();
        leaveRepository.save(l1);

        // 2. Maladie Approuvé (Emp 4: Ahmed)
        Leave l2 = Leave.builder()
                .employeeId(emp4Id)
                .startDate(now.withDayOfMonth(12))
                .endDate(now.withDayOfMonth(15))
                .type(LeaveType.SICK)
                .status(LeaveStatus.APPROVED)
                .reason("Grippe saisonnière")
                .createdAt(LocalDateTime.now().minusDays(8))
                .build();
        leaveRepository.save(l2);

        // 3. RTT Approuvé (Emp 1: Thomas)
        Leave l3 = Leave.builder()
                .employeeId(emp1Id)
                .startDate(now.withDayOfMonth(18))
                .endDate(now.withDayOfMonth(19))
                .type(LeaveType.OTHER)
                .status(LeaveStatus.APPROVED)
                .reason("Jour de RTT")
                .createdAt(LocalDateTime.now().minusDays(5))
                .build();
        leaveRepository.save(l3);

        // 4. Congé Payé En attente (Emp 2: Marc)
        Leave l4 = Leave.builder()
                .employeeId(emp2Id)
                .startDate(now.withDayOfMonth(22))
                .endDate(now.withDayOfMonth(25))
                .type(LeaveType.ANNUAL)
                .status(LeaveStatus.PENDING)
                .reason("Repos familial")
                .createdAt(LocalDateTime.now().minusDays(2))
                .build();
        leaveRepository.save(l4);

        // 5. Maladie En attente (Emp 3: Sophie)
        Leave l5 = Leave.builder()
                .employeeId(emp3Id)
                .startDate(now.plusMonths(1).withDayOfMonth(2))
                .endDate(now.plusMonths(1).withDayOfMonth(5))
                .type(LeaveType.SICK)
                .status(LeaveStatus.PENDING)
                .reason("Consultation et repos médical")
                .createdAt(LocalDateTime.now().minusDays(1))
                .build();
        leaveRepository.save(l5);
    }
}