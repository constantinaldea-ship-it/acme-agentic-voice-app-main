## deploy mock

./scripts/cloud/deploy.sh service mock-server ac-pbconvai-dev-project

gcloud projects get-iam-policy ac-pbconvai-dev-project \
  --flatten="bindings[].members" \
  --filter="bindings.members:user:constantin.aldea@breakingwave.io" \
  --format="table(bindings.role)"

  gcloud projects get-iam-policy ac-pbconvai-dev-project \
  --format=json | jq -r '.bindings[] | select(.members[]? | contains("constantin.aldea@breakingwave.io")) | .role'