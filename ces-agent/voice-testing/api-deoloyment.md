curl -X POST \
  -H "Authorization: Bearer $(gcloud auth print-access-token)" \
  -H "Content-Type: application/json" \
  -d '{
        "config": {
          "session": "projects/ac-pbconvai-dev-project/locations/eu/apps/acme-voice-eu/sessions/22uNahRbnjSiaSD",
          "app_version": "projects/ac-pbconvai-dev-project/locations/eu/apps/acme-voice-eu/versions/29a6248c-3922-47f5-8974-30885cf57810",
          "deployment": "projects/ac-pbconvai-dev-project/locations/eu/apps/acme-voice-eu/deployments/0d74692a-ef5c-43c2-a563-d71f76869903",
        },
        "inputs": [
          {
            "text": "hi"
          }
        ]
      }' \
  "https://ces.googleapis.com/v1beta/projects/ac-pbconvai-dev-project/locations/eu/apps/acme-voice-eu/sessions/22uNahRbnjSiaSD:runSession"