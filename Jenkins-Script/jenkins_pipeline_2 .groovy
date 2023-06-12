pipeline {
    agent any
    environment {
        Git_Hub_URL             = 'https://github.com/GOMATHISANKAR22/ECS_Fargate_Infrastructure.git'
        Workspace_name          = 'Pipeline_2'
        Cloudformation_Template = 'ECS-Fargate.yaml'
        Bucket_Name             = 'awsstoragedeploy123'
        Image_Name              = 'test1'
        Region_Name             = 'us-east-1'
        Aws_Id                  = '811187436843'
        Stack_Name              = 'my-stack'
        MailToRecipients        = 'sairamgs22@gmail.com'
        SonarQube_Report_URL    = 'http://54.91.169.96:9000/dashboard?id=New'
        Bucket_Name_Prod        = 'productionbucket2210'
        Prod_Template           = 'ECS-Fargate-Prod.yaml'
        Prod_Stack_Name         = 'my-stack-prod'
    }
    parameters {
        string  (name: 'Version_Number', defaultValue: '1.0', description: 'Version Number')

        choice  (choices: ["Baseline", "Full"],
                 description: 'Type of scan that is going to perform inside the container',
                 name: 'SCAN_TYPE')
 
      //  string (defaultValue: "http://my-st-loadb-13OBTKDSCUDQ3-776260702.us-east-1.elb.amazonaws.com",
        //         description: 'Target URL to scan',
          //       name: 'TARGET')
 
        booleanParam (defaultValue: true,
                 description: 'Parameter to know if wanna generate report.',
                 name: 'GENERATE_REPORT')
    }
    
    stages {
        stage('Clone') {
            steps {
                git branch: 'main', credentialsId: 'edb9612e-f882-4b86-ae44-cae1cadd438a', url: "${Git_Hub_URL}"
            }
        }
        stage('Copy to S3') {
            steps {
                
                sh '''
                aws s3 mb s3://${Bucket_Name}
                aws s3api put-bucket-versioning --bucket ${Bucket_Name} --versioning-configuration Status=Enabled
                aws s3 cp /var/lib/jenkins/workspace/${Workspace_name}/${Cloudformation_Template} s3://${Bucket_Name}/ 
                '''
            }
        }
        stage('SonarQube Analysis') {
            steps {
            script {
                def scannerHome = tool 'sonarqube'; 
                withSonarQubeEnv('Default')  {
                sh "${scannerHome}/bin/sonar-scanner -Dsonar.projectKey=New"
            
            }
        }
        }
        }
        stage('Send Sonar Analysis Report and Approval Email for Build Image') {
            steps {
                emailext (
                    subject: "Approval Needed to Build Docker Image",
                    body: "SonarQube Analysis Report URL: ${SonarQube_Report_URL} \n Username: admin /n Password: OZ@Z!JI.OlT0 \n Please Approve to Build the Docker Image in Testing Environment\n\n${BUILD_URL}input/",
                    mimeType: 'text/html',
                    recipientProviders: [[$class: 'CulpritsRecipientProvider'], [$class: 'RequesterRecipientProvider']],
                    from: "nithincloudnativegmail.com",
                    to: "${MailToRecipients}",              
                )
            }
        }
        
        stage('Approval-Build Image') {
            steps {
                timeout(time: 30, unit: 'MINUTES') {
                    input message: 'Please approve the build image process by clicking the link provided in the email.', ok: 'Proceed'
                }
            }
        }
        stage('Build') {
            steps {
                sh '''
                cd /var/lib/jenkins/workspace/${Workspace_name}
                sudo docker build -t ${Image_Name} .
                sudo chmod 666 /var/run/docker.sock
                '''
            }
        }
        stage('Push') {
            steps {
                sh '''
                sudo aws --region ${Region_Name} ecr get-login-password | sudo docker login --username AWS --password-stdin ${Aws_Id}.dkr.ecr.${Region_Name}.amazonaws.com
                sudo aws ecr create-repository --repository-name ${Image_Name} --region ${Region_Name} || true     
                sudo docker tag ${Image_Name}:latest ${Aws_Id}.dkr.ecr.${Region_Name}.amazonaws.com/${Image_Name}:${Version_Number}
                sudo docker push ${Aws_Id}.dkr.ecr.${Region_Name}.amazonaws.com/${Image_Name}:${Version_Number}
                '''
            }
        }
        stage('Send Approval Email for Build Stack') {
            steps {
                emailext (
                    subject: "Approval Needed to Cloudformation Stack",
                    body: "Please Approve to Deploy cloudformation stack in Testing Environment\n\n${BUILD_URL}input/",
                    mimeType: 'text/html',
                    recipientProviders: [[$class: 'CulpritsRecipientProvider'], [$class: 'RequesterRecipientProvider']],
                    from: "nithincloudnative@gmail.com",
                    to: "${MailToRecipients}",
                    
                )
            }
        }
        
        stage('Approval-Build Stack') {
            steps {
                timeout(time: 30, unit: 'MINUTES') {
                    input message: 'Please approve the deploy process by clicking the link provided in the email.', ok: 'Proceed'
                }
            }
        }
        stage('Check Stack Existence') {
            steps {
                script {
                    def stackExists = sh(
                        returnStatus: true,
                        script: 'aws cloudformation describe-stacks --stack-name ${Stack_Name}'
                    )
                    if (stackExists == 0) {
                        script {
                            sh '''
                            sudo aws cloudformation update-stack --stack-name ${Stack_Name} --template-url https://${Bucket_Name}.s3.amazonaws.com/${Cloudformation_Template} --capabilities CAPABILITY_NAMED_IAM  --parameters ParameterKey=ImageId,ParameterValue=${Aws_Id}.dkr.ecr.${Region_Name}.amazonaws.com/${Image_Name}:${Version_Number} || true
                           '''
                        }
                    } else {
                        script {
                            sh '''
                            sudo aws cloudformation create-stack --stack-name ${Stack_Name} --template-url https://${Bucket_Name}.s3.amazonaws.com/${Cloudformation_Template} --capabilities CAPABILITY_NAMED_IAM  --parameters ParameterKey=ImageId,ParameterValue=${Aws_Id}.dkr.ecr.${Region_Name}.amazonaws.com/${Image_Name}:${Version_Number}
                            '''
                        }
                    }

                }
            }
        }
       stage('Get loadbalancer DNS and save it in a file') {
            steps {
                script {
                    sh '''
                    sudo aws cloudformation describe-stacks --stack-name ${Stack_Name} --query "Stacks[0].Outputs[?OutputKey=='ALBEndpoint'].OutputValue" --output text > /var/lib/jenkins/workspace/${Workspace_name}/LoadBalancerDNS.txt
                    '''
                }
            }
        }
        stage('Setup owasp') {
            steps {
                script {
                    
                    echo "Pulling up last OWASP ZAP container --> Start"
                         sh 'docker pull owasp/zap2docker-stable'
                         echo "Pulling up last VMS container --> End"
                         echo "Starting container --> Start"
                         sh """
                         docker run -dt --name owasp \
                         owasp/zap2docker-stable \
                         /bin/bash
                         """
                }
            }
        }
        stage('Prepare wrk directory') {
             when {
                         environment name : 'GENERATE_REPORT', value: 'true'
             }
             steps {
                 script {
                         sh """
                             docker exec owasp \
                             mkdir /zap/wrk
                         """
                     }
                 }
         }
         stage('Scanning target on owasp container') {
             steps {
                 script {
                    def ALBEndpoint = sh(
                        returnStdout: true,
                        script: 'cat /var/lib/jenkins/workspace/${Workspace_name}/LoadBalancerDNS.txt'
                    )
                     scan_type = "${params.SCAN_TYPE}"
                     echo "----> scan_type: $scan_type"
                     target = "http://${ALBEndpoint}/"
                     if(scan_type == "Baseline"){
                         sh """
                             docker exec owasp zap-baseline.py -t $target -x report.xml -I 
                         """
                     }
                    else if(scan_type == "Full"){
                         sh """
                             docker exec owasp zap-full-scan.py -t $target -x report.xml -I 
                         """
                          }
                     else{
                         echo "Something went wrong..."
                     }
                 }
             }
         }
         stage('Copy Report to Workspace'){
             steps {
                 script {
                     sh '''
                         
                         docker cp owasp:/zap/wrk/report.xml ${WORKSPACE}/report.xml
                         docker stop owasp
                         docker rm owasp
                     '''
                 }
             }
         }
         
        stage('Send Approval Email for Production') {
            steps {
                emailext (
                    subject: "Approval Needed to Production Environment",
                    body: "Please Verify the Testing Environment and give approval to Production Environment\n\n${BUILD_URL}input/",
                    mimeType: 'text/html',
                    recipientProviders: [[$class: 'CulpritsRecipientProvider'], [$class: 'RequesterRecipientProvider']],
                    from: "nithincloudnative@gmail.com",
                    to: "${MailToRecipients}",
                    attachLog: true
                )
            }
        }
        
        stage('Approval-production') {
            steps {
                timeout(time: 900, unit: 'MINUTES') {
                    input message: 'Please approve the deploy process by clicking the link provided in the email.', ok: 'Proceed'
                }
            }
        }
        stage('Copy production.yml to s3') {
            steps {
                sh '''
                aws s3 mb s3://${Bucket_Name_Prod}
                aws s3api put-bucket-versioning --bucket ${Bucket_Name_Prod} --versioning-configuration Status=Enabled
                aws s3 cp /var/lib/jenkins/workspace/${Workspace_name}/${Prod_Template} s3://${Bucket_Name_Prod}/ 
                '''
            }
            }
        stage('Deploy Production') {
            steps {
                script {
                    def stackExists = sh(
                        returnStatus: true,
                        script: 'aws cloudformation describe-stacks --stack-name ${Prod_Stack_Name}'
                    )
                    if (stackExists == 0) {
                        script {
                            sh '''
                            sudo aws cloudformation update-stack --stack-name ${Prod_Stack_Name} --template-url https://${Bucket_Name_Prod}.s3.amazonaws.com/${Prod_Template} --capabilities CAPABILITY_NAMED_IAM  --parameters ParameterKey=ImageId,ParameterValue=${Aws_Id}.dkr.ecr.${Region_Name}.amazonaws.com/${Image_Name}:${Version_Number} || true
                            '''
                        }
                    } else {
                        script {
                            sh '''
                            sudo aws cloudformation create-stack --stack-name ${Prod_Stack_Name} --template-url https://${Bucket_Name_Prod}.s3.amazonaws.com/${Prod_Template} --capabilities CAPABILITY_NAMED_IAM  --parameters ParameterKey=ImageId,ParameterValue=${Aws_Id}.dkr.ecr.${Region_Name}.amazonaws.com/${Image_Name}:${Version_Number}
                            '''
                        }
                    }
                }
            }
        }
    }
}
