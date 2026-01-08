#!/usr/bin/env python3
"""
download_did_data.py

国土数値情報から人口集中地区（DID）データをダウンロードし、
GeoJSON形式に変換するスクリプト。

使用方法:
    pip install requests geopandas shapely fiona
    python download_did_data.py

出力:
    app/src/main/assets/layers/did_japan.geojson

データソース:
    国土数値情報 A16（人口集中地区）
    https://nlftp.mlit.go.jp/ksj/gml/datalist/KsjTmplt-A16-v2_3.html
"""

import os
import sys
import zipfile
import tempfile
import requests
import json
from pathlib import Path

# オプション: geopandas がインストールされている場合のみ使用
try:
    import geopandas as gpd
    HAS_GEOPANDAS = True
except ImportError:
    HAS_GEOPANDAS = False
    print("警告: geopandas がインストールされていません。")
    print("インストール方法: pip install geopandas")

# 国土数値情報のベースURL
BASE_URL = "https://nlftp.mlit.go.jp/ksj/gml/data/A16/A16-15/"

# 都道府県コードとファイル名のマッピング（令和2年国勢調査ベース）
# 注: 実際のファイル名は国土数値情報サイトで確認が必要
PREFECTURES = {
    "01": "北海道",
    "02": "青森県",
    "03": "岩手県",
    "04": "宮城県",
    "05": "秋田県",
    "06": "山形県",
    "07": "福島県",
    "08": "茨城県",
    "09": "栃木県",
    "10": "群馬県",
    "11": "埼玉県",
    "12": "千葉県",
    "13": "東京都",
    "14": "神奈川県",
    "15": "新潟県",
    "16": "富山県",
    "17": "石川県",
    "18": "福井県",
    "19": "山梨県",
    "20": "長野県",
    "21": "岐阜県",
    "22": "静岡県",
    "23": "愛知県",
    "24": "三重県",
    "25": "滋賀県",
    "26": "京都府",
    "27": "大阪府",
    "28": "兵庫県",
    "29": "奈良県",
    "30": "和歌山県",
    "31": "鳥取県",
    "32": "島根県",
    "33": "岡山県",
    "34": "広島県",
    "35": "山口県",
    "36": "徳島県",
    "37": "香川県",
    "38": "愛媛県",
    "39": "高知県",
    "40": "福岡県",
    "41": "佐賀県",
    "42": "長崎県",
    "43": "熊本県",
    "44": "大分県",
    "45": "宮崎県",
    "46": "鹿児島県",
    "47": "沖縄県",
}

def download_file(url: str, dest_path: str) -> bool:
    """ファイルをダウンロード"""
    try:
        print(f"ダウンロード中: {url}")
        response = requests.get(url, stream=True, timeout=60)
        response.raise_for_status()
        
        with open(dest_path, 'wb') as f:
            for chunk in response.iter_content(chunk_size=8192):
                f.write(chunk)
        
        print(f"  -> 保存完了: {dest_path}")
        return True
    except Exception as e:
        print(f"  -> ダウンロード失敗: {e}")
        return False

def extract_shapefile(zip_path: str, extract_dir: str) -> str:
    """ZIPからShapefileを抽出"""
    with zipfile.ZipFile(zip_path, 'r') as zip_ref:
        zip_ref.extractall(extract_dir)
    
    # .shpファイルを探す
    for root, dirs, files in os.walk(extract_dir):
        for file in files:
            if file.endswith('.shp'):
                return os.path.join(root, file)
    
    return None

def shapefile_to_geojson(shp_path: str) -> dict:
    """ShapefileをGeoJSONに変換"""
    if not HAS_GEOPANDAS:
        raise RuntimeError("geopandas が必要です")
    
    gdf = gpd.read_file(shp_path, encoding='cp932')
    
    # 座標系をWGS84に変換
    if gdf.crs and gdf.crs.to_epsg() != 4326:
        gdf = gdf.to_crs(epsg=4326)
    
    return json.loads(gdf.to_json())

def simplify_geojson(geojson: dict, tolerance: float = 0.0001) -> dict:
    """GeoJSONを簡略化してファイルサイズを削減"""
    if not HAS_GEOPANDAS:
        return geojson
    
    gdf = gpd.GeoDataFrame.from_features(geojson['features'])
    gdf['geometry'] = gdf['geometry'].simplify(tolerance, preserve_topology=True)
    
    return json.loads(gdf.to_json())

def create_sample_did_geojson() -> dict:
    """
    サンプルDIDデータを作成（実際のデータが取得できない場合のフォールバック）
    
    注意: これは実際のDIDデータではありません。
    正確なデータは国土数値情報からダウンロードしてください。
    """
    print("\n" + "="*60)
    print("注意: サンプルデータを生成しています。")
    print("正確なDIDデータを使用するには、以下の手順に従ってください:")
    print("")
    print("1. 国土数値情報ダウンロードサイトにアクセス:")
    print("   https://nlftp.mlit.go.jp/ksj/gml/datalist/KsjTmplt-A16-v2_3.html")
    print("")
    print("2. 必要な都道府県のデータをダウンロード")
    print("")
    print("3. QGIS等でShapefileをGeoJSONに変換:")
    print("   - QGISでShapefileを開く")
    print("   - レイヤ → エクスポート → 地物を別名で保存")
    print("   - 形式: GeoJSON、座標系: EPSG:4326")
    print("")
    print("4. 変換したGeoJSONを以下に配置:")
    print("   app/src/main/assets/layers/did_japan.geojson")
    print("="*60 + "\n")
    
    return {
        "type": "FeatureCollection",
        "name": "人口集中地区（DID）- サンプルデータ",
        "note": "これはサンプルデータです。正確なデータは国土数値情報からダウンロードしてください。",
        "source": "https://nlftp.mlit.go.jp/ksj/gml/datalist/KsjTmplt-A16-v2_3.html",
        "features": []
    }

def main():
    # プロジェクトルートを特定
    script_dir = Path(__file__).parent
    project_root = script_dir.parent
    assets_dir = project_root / "app" / "src" / "main" / "assets" / "layers"
    
    # 出力ディレクトリを作成
    assets_dir.mkdir(parents=True, exist_ok=True)
    
    output_file = assets_dir / "did_japan.geojson"
    
    print("="*60)
    print("国土数値情報 人口集中地区（DID）データ取得ツール")
    print("="*60)
    
    if not HAS_GEOPANDAS:
        print("\ngeopandas がインストールされていないため、")
        print("サンプルデータのみを生成します。")
        print("\n正確なデータを使用するには:")
        print("  pip install geopandas requests")
        print("を実行してから、このスクリプトを再実行してください。")
        
        geojson = create_sample_did_geojson()
    else:
        # 実際のデータをダウンロードする場合の処理
        # 注: 国土数値情報のダウンロードには利用規約への同意が必要です
        print("\n現在、自動ダウンロードは実装されていません。")
        print("手動でデータをダウンロード・変換してください。")
        
        geojson = create_sample_did_geojson()
    
    # GeoJSONを保存
    with open(output_file, 'w', encoding='utf-8') as f:
        json.dump(geojson, f, ensure_ascii=False, indent=2)
    
    print(f"\n出力ファイル: {output_file}")
    print(f"ファイルサイズ: {output_file.stat().st_size / 1024:.1f} KB")
    
    print("\n次のステップ:")
    print("1. 国土数値情報から正式なDIDデータをダウンロード")
    print("2. GeoJSONに変換して did_japan.geojson を置き換え")
    print("3. アプリをビルド")

if __name__ == "__main__":
    main()


