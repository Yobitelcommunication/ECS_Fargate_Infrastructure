pipeline {
    agent any
    environment {
        Git_Hub_URL             = 'https://github.com/GOMATHISANKAR22/DockerFile.git'
        Workspace_name          = 'ECS_DEPLOY'
        Cloudformation_Template = 'ECS-Fargate.yaml'
        Bucket_Name             = 'awsstoragedeploy123'
        Image_Name              = 'test1'
        Region_Name             = 'us-east-1'
        Aws_Id                  = '811187436843'
        Stack_Name              = 'my-stack'
        MailToRecipients        = 'kgomathisankar22@gmail.com'
        SonarQube_Report_URL    = 'http://3.80.81.84:9000/dashboard?id=New'
    }
    parameters {
        string(name: 'Version_Number', defaultValue: '1.0', description: 'Version Number')
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
        stage('SonarQube Analysis Report') {
            steps {
              
                emailext (
                    subject: "SonarQube Analysis Report",
                    body: "SonarQube Analysis Report URL: ${SonarQube_Report_URL} \n Username: admin /n Password: OZ@Z!JI.OlT0",
                    mimeType: 'text/html',
                    recipientProviders: [[$class: 'CulpritsRecipientProvider'], [$class: 'RequesterRecipientProvider']],
                    from: "nithincloudnative@gmail.com",
                    to: "${MailToRecipients}",
                    
                )
            }
        }
        stage('Send Approval Email for Build Image') {
            steps {
                emailext (
                    subject: "Approval Needed to Build Docker Image",
                    body: "Please Approve to Build the Docker Image in Testing Environment\n\n${BUILD_URL}input/",
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
                '''
            }
        }
        stage('Push') {
            steps {
                sh '''
                sudo aws ecr create-repository --repository-name ${Image_Name} --region ${Region_Name} || true     
                sudo aws ecr get-login-password --region ${Region_Name} | docker login --username AWS --password-stdin ${Aws_Id}.dkr.ecr.${Region_Name}.amazonaws.com
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
    }
}
