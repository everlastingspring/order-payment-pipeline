#!/bin/bash
# EC2 bootstrap script for Ubuntu 22.04 (t3.xlarge recommended).
# Run once after launching the instance:
#   chmod +x ec2-setup.sh && ./ec2-setup.sh
#
# After this script completes, log out and back in (docker group takes effect),
# then run:
#   export ECR_REGISTRY=<account-id>.dkr.ecr.ap-south-1.amazonaws.com
#   aws ecr get-login-password --region ap-south-1 | docker login --username AWS --password-stdin $ECR_REGISTRY
#   docker compose -f docker-compose-aws.yml up -d

set -euo pipefail

AWS_REGION="ap-south-1"

echo "==> Updating system packages"
sudo apt-get update -y
sudo apt-get upgrade -y

echo "==> Installing Docker CE"
sudo apt-get install -y ca-certificates curl gnupg lsb-release
sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
sudo chmod a+r /etc/apt/keyrings/docker.gpg
echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu \
  $(lsb_release -cs) stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
sudo apt-get update -y
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

echo "==> Adding current user to docker group"
sudo usermod -aG docker "${USER}"

echo "==> Starting Docker service"
sudo systemctl enable docker
sudo systemctl start docker

echo "==> Installing AWS CLI v2"
curl -fsSL "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o /tmp/awscliv2.zip
sudo apt-get install -y unzip
unzip -q /tmp/awscliv2.zip -d /tmp
sudo /tmp/aws/install
rm -rf /tmp/awscliv2.zip /tmp/aws

echo "==> Installing k6 (load testing)"
sudo gpg --no-default-keyring --keyring /usr/share/keyrings/k6-archive-keyring.gpg \
  --keyserver hkp://keyserver.ubuntu.com:80 --recv-keys C5AD17C747E3415A3642D57D77C6C491D6AC1D69
echo "deb [signed-by=/usr/share/keyrings/k6-archive-keyring.gpg] https://dl.k6.io/deb stable main" \
  | sudo tee /etc/apt/sources.list.d/k6.list
sudo apt-get update -y
sudo apt-get install -y k6

echo "==> Installing git and other utilities"
sudo apt-get install -y git jq htop

echo ""
echo "============================================================"
echo "  Setup complete."
echo ""
echo "  NEXT STEPS:"
echo "  1. Log out and back in for docker group to take effect."
echo "  2. Configure AWS credentials:"
echo "       aws configure"
echo "     (or use an EC2 instance role — recommended)"
echo "  3. Clone the repo:"
echo "       git clone <your-repo-url>"
echo "  4. Login to ECR and start services:"
echo "       export ECR_REGISTRY=<account-id>.dkr.ecr.${AWS_REGION}.amazonaws.com"
echo "       aws ecr get-login-password --region ${AWS_REGION} \\"
echo "         | docker login --username AWS --password-stdin \$ECR_REGISTRY"
echo "       docker compose -f docker-compose-aws.yml pull"
echo "       docker compose -f docker-compose-aws.yml up -d"
echo "  5. Wait ~3 minutes for all services to be healthy, then run:"
echo "       k6 run -e BASE_URL=http://localhost:8080 \\"
echo "               -e KEYCLOAK_URL=http://localhost:8180 \\"
echo "               scripts/load-tests/k6-smoke.js"
echo "============================================================"
