// =====================================================================
// Pipeline CI/CD des services serveur : backend RH (Spring Boot, WAR, Tomcat)
// et microservice IA (FastAPI). Chaque service n'est construit que si son
// dossier a été modifié (ou si le paramètre TOUT_CONSTRUIRE est coché).
// =====================================================================

/** Vrai si l'un des commits de ce build modifie un fichier du dossier donné. */
def dossierModifie(String dossier) {
    for (changeSet in currentBuild.changeSets) {
        for (entry in changeSet.items) {
            for (fichier in entry.affectedFiles) {
                if (fichier.path.startsWith(dossier)) {
                    return true
                }
            }
        }
    }
    return false
}

/** Vérifie si SonarQube répond. */
def sonarDisponible(String url) {
    return sh(script: "curl -sf ${url}/api/system/status | grep -q '\"status\":\"UP\"'",
              returnStatus: true) == 0
}

pipeline {
    agent any

    options {
        timestamps()
        buildDiscarder(logRotator(numToKeepStr: '10', artifactNumToKeepStr: '3'))
        disableConcurrentBuilds()
        timeout(time: 150, unit: 'MINUTES')
    }

    parameters {
        booleanParam(name: 'TOUT_CONSTRUIRE', defaultValue: false,
                     description: 'Construire le backend ET le microservice IA, même sans modification')
    }

    triggers {
        pollSCM('H/5 * * * *')
    }

    environment {
        REGISTRY       = '192.168.255.129:8082'
        BACKEND_IMAGE  = "${REGISTRY}/hr-backend"
        IA_IMAGE       = "${REGISTRY}/cv-matcher"
        TEST_CONTAINER = "cv-matcher-tests-${env.BUILD_NUMBER}"
        SONAR_URL      = 'http://192.168.255.129:9000'
    }

    stages {

        // =============================================================
        // Préparation
        // =============================================================

        stage('Préparation') {
            steps {
                script {
                    // Version = numéro du build + commit Git : chaque image est traçable jusqu'au code
                    // Version = numéro du build + commit Git (si le code vient bien de GitHub)
                    env.IMAGE_TAG = env.GIT_COMMIT ? "${env.BUILD_NUMBER}-${env.GIT_COMMIT.take(7)}" : "${env.BUILD_NUMBER}"
                    env.BUILD_BACKEND = (params.TOUT_CONSTRUIRE || dossierModifie('hr-backend/')) ? 'true' : 'false'
                    env.BUILD_IA      = (params.TOUT_CONSTRUIRE || dossierModifie('cv-matcher/')) ? 'true' : 'false'
                    env.SONAR_UP      = sonarDisponible(env.SONAR_URL) ? 'true' : 'false'

                    echo "Version des images : ${env.IMAGE_TAG}"
                    echo "Backend à construire : ${env.BUILD_BACKEND}"
                    echo "Microservice IA à construire : ${env.BUILD_IA}"
                    echo(env.SONAR_UP == 'true' ? 'SonarQube disponible' : 'SonarQube arrêté : analyses ignorées')

                    if (env.BUILD_BACKEND == 'false' && env.BUILD_IA == 'false') {
                        echo 'Aucun changement dans hr-backend/ ni cv-matcher/ : rien à construire.'
                    }
                }
            }
        }

        // =============================================================
        // Backend RH
        // =============================================================

        stage('Backend') {
            when { environment name: 'BUILD_BACKEND', value: 'true' }
            stages {

                stage('Backend - Compilation et tests') {
                    steps {
                        dir('hr-backend') {
                            sh 'chmod +x mvnw'
                            sh """
                                ./mvnw -B clean verify \
                                  -Dtest='!HrBackendApplicationTests' \
                                  -Dsurefire.failIfNoSpecifiedTests=false
                            """
                        }
                    }
                    post {
                        always {
                            junit allowEmptyResults: true, testResults: 'hr-backend/target/surefire-reports/*.xml'
                        }
                    }
                }

                stage('Backend - Analyse SonarQube') {
                    when { environment name: 'SONAR_UP', value: 'true' }
                    steps {
                        catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
                            dir('hr-backend') {
                                withSonarQubeEnv('sonarqube') {
                                    sh '''
                                        ./mvnw -B org.sonarsource.scanner.maven:sonar-maven-plugin:sonar \
                                          -Dsonar.projectKey=hr-backend \
                                          -Dsonar.projectName="CorpFlow HR - Backend" \
                                          -Dsonar.scm.disabled=true \
                                          -Dsonar.coverage.jacoco.xmlReportPaths=target/site/jacoco/jacoco.xml
                                    '''
                                }
                                script { env.BACKEND_ANALYSE_OK = 'true' }
                            }
                        }
                    }
                }

                stage('Backend - Quality Gate') {
                    when { environment name: 'BACKEND_ANALYSE_OK', value: 'true' }
                    steps {
                        catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
                            timeout(time: 5, unit: 'MINUTES') {
                                waitForQualityGate abortPipeline: false
                            }
                        }
                    }
                }

                stage('Backend - Archivage du WAR') {
                    steps {
                        archiveArtifacts artifacts: 'hr-backend/target/hr-backend.war', fingerprint: true
                    }
                }

                stage('Backend - Image Docker') {
                    steps {
                        dir('hr-backend') {
                            sh 'docker build -t "$BACKEND_IMAGE:$IMAGE_TAG" -t "$BACKEND_IMAGE:latest" .'
                        }
                    }
                }

                stage('Backend - Envoi vers Nexus') {
                    steps {
                        withCredentials([usernamePassword(credentialsId: 'nexus-docker',
                                                          usernameVariable: 'NEXUS_USER',
                                                          passwordVariable: 'NEXUS_PWD')]) {
                            sh '''
                                echo "$NEXUS_PWD" | docker login "$REGISTRY" -u "$NEXUS_USER" --password-stdin
                                docker push -q "$BACKEND_IMAGE:$IMAGE_TAG"
                                docker push -q "$BACKEND_IMAGE:latest"
                            '''
                        }
                    }
                }

                stage('Backend - Déploiement Kubernetes') {
                    steps {
                        withCredentials([file(credentialsId: 'kubeconfig', variable: 'KUBECONFIG')]) {
                            sh '''
                                kubectl -n hr-platform set image deployment/hr-backend hr-backend="$BACKEND_IMAGE:$IMAGE_TAG"
                                if ! kubectl -n hr-platform rollout status deployment/hr-backend --timeout=300s; then
                                    echo "Échec du déploiement du backend : retour à la version précédente"
                                    kubectl -n hr-platform rollout undo deployment/hr-backend
                                    exit 1
                                fi
                            '''
                        }
                    }
                }
            }
        }

        // =============================================================
        // Microservice IA
        // =============================================================

        stage('Microservice IA') {
            when { environment name: 'BUILD_IA', value: 'true' }
            stages {

                stage('IA - Image Docker') {
                    steps {
                        dir('cv-matcher') {
                            sh 'docker build -t "$IA_IMAGE:$IMAGE_TAG" -t "$IA_IMAGE:latest" .'
                        }
                    }
                }

                stage('IA - Tests dans l\'image') {
                    steps {
                        dir('cv-matcher') {
                            sh '''
                                docker run --name "$TEST_CONTAINER" \
                                  -v "$PWD/tests:/app/tests:ro" \
                                  -e HF_HUB_OFFLINE=1 \
                                  "$IA_IMAGE:$IMAGE_TAG" \
                                  sh -c "pip install -q --no-cache-dir pytest httpx pytest-cov && python -m pytest -q tests --junitxml=/tmp/pytest.xml --cov=app --cov-report=xml:/tmp/coverage.xml"
                            '''
                        }
                    }
                    post {
                        always {
                            sh '''
                                docker cp "$TEST_CONTAINER:/tmp/pytest.xml" cv-matcher/pytest.xml || true
                                docker cp "$TEST_CONTAINER:/tmp/coverage.xml" cv-matcher/coverage.xml || true
                                docker rm -f "$TEST_CONTAINER" || true
                                sed -i 's#<source>/app#<source>/usr/src#' cv-matcher/coverage.xml || true
                            '''
                            junit allowEmptyResults: true, testResults: 'cv-matcher/pytest.xml'
                        }
                    }
                }

                stage('IA - Analyse SonarQube') {
                    when { environment name: 'SONAR_UP', value: 'true' }
                    steps {
                        catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
                            dir('cv-matcher') {
                                withSonarQubeEnv('sonarqube') {
                                    sh '''
                                        docker run --rm \
                                          --memory 2g \
                                          -u "$(id -u):$(id -g)" \
                                          -e SONAR_HOST_URL="$SONAR_HOST_URL" \
                                          -e SONAR_TOKEN="$SONAR_AUTH_TOKEN" \
                                          -e SONAR_USER_HOME=/usr/src/.sonar \
                                          -e SONAR_SCANNER_OPTS="-Xmx512m" \
                                          -v "$PWD:/usr/src" \
                                          sonarsource/sonar-scanner-cli \
                                          -Dsonar.projectKey=cv-matcher \
                                          -Dsonar.projectName="CorpFlow HR - Microservice IA" \
                                          -Dsonar.working.directory=/usr/src/.scannerwork \
                                          -Dsonar.scm.disabled=true \
                                          -Dsonar.sources=app \
                                          -Dsonar.tests=tests \
                                          -Dsonar.python.version=3.11 \
                                          -Dsonar.python.coverage.reportPaths=coverage.xml
                                    '''
                                }
                                script { env.IA_ANALYSE_OK = 'true' }
                            }
                        }
                    }
                }

                stage('IA - Quality Gate') {
                    when { environment name: 'IA_ANALYSE_OK', value: 'true' }
                    steps {
                        catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
                            timeout(time: 5, unit: 'MINUTES') {
                                waitForQualityGate abortPipeline: false
                            }
                        }
                    }
                }

                stage('IA - Envoi vers Nexus') {
                    steps {
                        withCredentials([usernamePassword(credentialsId: 'nexus-docker',
                                                          usernameVariable: 'NEXUS_USER',
                                                          passwordVariable: 'NEXUS_PWD')]) {
                            sh '''
                                echo "$NEXUS_PWD" | docker login "$REGISTRY" -u "$NEXUS_USER" --password-stdin
                                docker push -q "$IA_IMAGE:$IMAGE_TAG"
                                docker push -q "$IA_IMAGE:latest"
                            '''
                        }
                    }
                }

                stage('IA - Déploiement Kubernetes') {
                    steps {
                        withCredentials([file(credentialsId: 'kubeconfig', variable: 'KUBECONFIG')]) {
                            sh '''
                                kubectl -n hr-platform set image deployment/cv-matcher cv-matcher="$IA_IMAGE:$IMAGE_TAG"
                                if ! kubectl -n hr-platform rollout status deployment/cv-matcher --timeout=900s; then
                                    echo "Échec du déploiement du microservice IA : retour à la version précédente"
                                    kubectl -n hr-platform rollout undo deployment/cv-matcher
                                    exit 1
                                fi
                            '''
                        }
                    }
                }
            }
        }
    }

    post {
        always {
            sh 'docker logout "$REGISTRY" || true'
            sh '''
                docker rmi "$BACKEND_IMAGE:$IMAGE_TAG" "$BACKEND_IMAGE:latest" 2>/dev/null || true
                docker rmi "$IA_IMAGE:$IMAGE_TAG" "$IA_IMAGE:latest" 2>/dev/null || true
            '''
        }
        success {
            echo "Services serveur livrés en version ${env.IMAGE_TAG}"
        }
    }
}