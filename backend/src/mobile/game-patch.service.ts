import { Injectable, Logger } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';

/** Shape of patches.json stored as an asset in the private GitHub release. */
interface PatchesIndex {
  supported?: {
    gameVersion?: string;
    tag?: string;
    asset?: string;
    sha256?: string;
    sizeBytes?: number;
    bridgeVersion?: string;
  };
}

export interface GamePatchInfo {
  available: boolean;
  gameVersion?: string;
  downloadUrl?: string;
  sha256?: string;
  sizeBytes?: number;
  bridgeVersion?: string;
}

interface GithubAsset {
  id: number;
  name: string;
  size: number;
}

interface GithubRelease {
  tag_name: string;
  assets: GithubAsset[];
}

const GITHUB_API = 'https://api.github.com';
const INDEX_ASSET_NAME = 'patches.json';

/**
 * Resolves the latest game patch from a private GitHub release. The GitHub token
 * never leaves the backend; the app receives only a short-lived signed asset URL
 * and downloads the APK directly from GitHub.
 */
@Injectable()
export class GamePatchService {
  private readonly logger = new Logger(GamePatchService.name);

  constructor(private readonly configService: ConfigService) {}

  private repo(): string | null {
    return this.configService.get<string>('GITHUB_PATCH_REPO')?.trim() || null;
  }

  private token(): string | null {
    return this.configService.get<string>('GITHUB_TOKEN')?.trim() || null;
  }

  private apiHeaders(): Record<string, string> {
    return {
      Authorization: `Bearer ${this.token()}`,
      Accept: 'application/vnd.github+json',
      'X-GitHub-Api-Version': '2022-11-28',
      'User-Agent': 'SquadRelay-Backend',
    };
  }

  async getLatestPatch(): Promise<GamePatchInfo> {
    const repo = this.repo();
    const token = this.token();
    if (!repo || !token) {
      return { available: false };
    }
    try {
      const release = await this.fetchLatestRelease(repo);
      if (!release) return { available: false };

      const index = await this.fetchIndex(repo, release);
      const supported = index?.supported;
      const assetName = supported?.asset;
      if (!supported?.gameVersion || !assetName) {
        this.logger.warn('patches.json missing supported.gameVersion/asset');
        return { available: false };
      }

      const apkAsset = release.assets.find((a) => a.name === assetName);
      if (!apkAsset) {
        this.logger.warn(`APK asset "${assetName}" not found in release ${release.tag_name}`);
        return { available: false };
      }

      const downloadUrl = await this.signedAssetUrl(repo, apkAsset.id);
      if (!downloadUrl) return { available: false };

      return {
        available: true,
        gameVersion: supported.gameVersion,
        downloadUrl,
        sha256: supported.sha256,
        sizeBytes: supported.sizeBytes ?? apkAsset.size,
        bridgeVersion: supported.bridgeVersion,
      };
    } catch (err) {
      this.logger.error(`Failed to resolve game patch: ${(err as Error).message}`);
      return { available: false };
    }
  }

  private async fetchLatestRelease(repo: string): Promise<GithubRelease | null> {
    const res = await fetch(`${GITHUB_API}/repos/${repo}/releases/latest`, {
      headers: this.apiHeaders(),
    });
    if (!res.ok) {
      this.logger.warn(`GitHub releases/latest returned ${res.status}`);
      return null;
    }
    return (await res.json()) as GithubRelease;
  }

  private async fetchIndex(repo: string, release: GithubRelease): Promise<PatchesIndex | null> {
    const indexAsset = release.assets.find((a) => a.name === INDEX_ASSET_NAME);
    if (!indexAsset) {
      this.logger.warn(`${INDEX_ASSET_NAME} not found in release ${release.tag_name}`);
      return null;
    }
    const res = await fetch(`${GITHUB_API}/repos/${repo}/releases/assets/${indexAsset.id}`, {
      headers: { ...this.apiHeaders(), Accept: 'application/octet-stream' },
    });
    if (!res.ok) {
      this.logger.warn(`Failed to download ${INDEX_ASSET_NAME}: ${res.status}`);
      return null;
    }
    return (await res.json()) as PatchesIndex;
  }

  /**
   * GitHub returns a 302 to a short-lived signed URL (no auth required) when an
   * asset is requested with Accept: application/octet-stream. We capture that
   * Location and hand it to the client so the heavy APK transfer skips the backend.
   */
  private async signedAssetUrl(repo: string, assetId: number): Promise<string | null> {
    const res = await fetch(`${GITHUB_API}/repos/${repo}/releases/assets/${assetId}`, {
      headers: { ...this.apiHeaders(), Accept: 'application/octet-stream' },
      redirect: 'manual',
    });
    const location = res.headers.get('location');
    if (res.status >= 300 && res.status < 400 && location) {
      return location;
    }
    this.logger.warn(`Expected redirect for asset ${assetId}, got ${res.status}`);
    return null;
  }
}
