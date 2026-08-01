#!/usr/bin/env python3
"""
Upload Jotdown v3.1.7 (build 27) to Google Play Console

Requirements:
- google-play-publisher: pip install google-play-publisher
- Service account JSON key from Play Console

Usage:
    python upload_play_console.py <service-account-key.json> [track]

Examples:
    python upload_play_console.py key.json internal
    python upload_play_console.py key.json staging
    python upload_play_console.py key.json production
"""

import sys
import json
from pathlib import Path

def upload_aab_to_play_console(service_account_key_path, track="internal"):
    """
    Upload AAB to Play Console.

    Args:
        service_account_key_path: Path to service account JSON
        track: internal, staging, or production
    """

    try:
        from googleapiclient.discovery import build
        from googleapiclient.http import MediaFileUpload
        from oauth2client.service_account import ServiceAccountCredentials
    except ImportError:
        print("Error: Required packages not installed.")
        print("Install with: pip install google-auth-oauthlib google-auth-httplib2 google-api-python-client")
        sys.exit(1)

    # Paths
    key_path = Path(service_account_key_path)
    aab_path = Path(__file__).parent / "app/build/outputs/bundle/fullRelease/app-full-release.aab"

    if not key_path.exists():
        print(f"Error: Service account key not found: {key_path}")
        sys.exit(1)

    if not aab_path.exists():
        print(f"Error: AAB not found: {aab_path}")
        print("Run: ./gradlew bundleFullRelease")
        sys.exit(1)

    print(f"📦 Uploading {aab_path.name} to {track} track...")

    # Load service account
    with open(key_path) as f:
        service_account_info = json.load(f)

    # Authenticate
    scope = ["https://www.googleapis.com/auth/androidpublisher"]
    credentials = ServiceAccountCredentials.from_json_keyfile_dict(
        service_account_info, scopes=scope
    )

    # Build service
    service = build("androidpublisher", "v3", credentials=credentials)
    package_name = "br.com.jotdown"

    # Upload AAB
    media = MediaFileUpload(aab_path, mimetype="application/octet-stream")

    try:
        edit_request = service.edits().insert(body={}, packageName=package_name)
        edit_response = edit_request.execute()
        edit_id = edit_response["id"]

        print(f"✓ Edit session created: {edit_id}")

        # Upload bundle
        upload_request = service.edits().bundles().upload(
            packageName=package_name,
            editId=edit_id,
            media_body=media
        )
        upload_response = upload_request.execute()
        bundle_version = upload_response["versionCode"]

        print(f"✓ Bundle uploaded: version {bundle_version}")

        # Create release
        track_data = {
            "track": track,
            "releases": [
                {
                    "name": f"Jotdown v3.1.7 (F0-F5 complete)",
                    "versionCodes": [str(bundle_version)],
                    "releaseNotes": [
                        {
                            "language": "en-US",
                            "text": "F0-F5 feature roadmap complete:\n• Reading status tracking\n• Research goals (Metas)\n• Atomic notes (Fichas)\n• Document-note linking\n• DOI import (CrossRef API)\n• Citation linking infrastructure\n\nBug fix: Note deletion UI now waits for DB completion.\ni18n: All strings localized (PT+EN)."
                        },
                        {
                            "language": "pt-BR",
                            "text": "Roadmap F0-F5 completo:\n• Rastreamento de status de leitura\n• Metas de pesquisa (Metas)\n• Notas atômicas (Fichas)\n• Vinculação documento-nota\n• Importação DOI (API CrossRef)\n• Infraestrutura de vínculo de citações\n\nCorreção: Deleção de ficha aguarda conclusão DB.\ni18n: Todas strings localizadas (PT+EN)."
                        }
                    ],
                    "status": "completed"
                }
            ]
        }

        update_request = service.edits().update(
            packageName=package_name,
            editId=edit_id,
            body=track_data
        )
        update_request.execute()

        print(f"✓ Release created in {track} track")

        # Commit
        commit_request = service.edits().commit(
            packageName=package_name,
            editId=edit_id
        )
        commit_response = commit_request.execute()

        print(f"✓ Release committed!")
        print(f"\n✅ Upload successful to {track} track")
        print(f"Review at: https://play.google.com/console/developers/8659849384270771889/app/4972729739779032576/releases")

    except Exception as e:
        print(f"❌ Error: {e}")
        sys.exit(1)

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(1)

    key_file = sys.argv[1]
    track = sys.argv[2] if len(sys.argv) > 2 else "internal"

    upload_aab_to_play_console(key_file, track)
