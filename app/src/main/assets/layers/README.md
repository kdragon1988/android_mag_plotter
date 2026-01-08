# 飛行制限区域レイヤーデータ

このディレクトリには、ドローン飛行制限区域のGeoJSONデータを配置します。

## 必要なファイル

| ファイル名 | 内容 | データソース |
|-----------|------|-------------|
| `did_japan.geojson` | 人口集中地区（DID） | 国土数値情報 A16 |
| `airport_restriction.geojson` | 空港等周辺の飛行禁止区域 | 国土交通省航空局 |
| `no_fly_zone.geojson` | 小型無人機等飛行禁止区域 | 警察庁 |

---

## 1. 人口集中地区（DID）データの取得

### データソース
国土数値情報ダウンロードサービス（国土交通省）
- URL: https://nlftp.mlit.go.jp/ksj/gml/datalist/KsjTmplt-A16-v2_3.html
- データID: A16（人口集中地区）
- 形式: Shapefile（GML）

### 取得手順

1. **国土数値情報サイトにアクセス**
   - https://nlftp.mlit.go.jp/ksj/ にアクセス
   - 「1.国土（水・土地）」→「A16 人口集中地区」を選択
   
2. **データをダウンロード**
   - 必要な都道府県のデータをダウンロード
   - 全国版を一括でダウンロードすることも可能

3. **GeoJSONに変換**（QGISを使用する場合）
   - QGISでShapefileを開く
   - レイヤ → エクスポート → 地物を別名で保存
   - 形式: GeoJSON
   - CRS: EPSG:4326 (WGS84)
   - ファイル名: `did_japan.geojson`

4. **ファイルサイズの最適化**（オプション）
   - QGISで「ベクタ」→「ジオメトリツール」→「ジオメトリを簡略化」
   - 許容値: 0.0001（約10m精度）
   - これにより、ファイルサイズを大幅に削減可能

### Pythonでの変換例

```python
import geopandas as gpd

# Shapefileを読み込み
gdf = gpd.read_file("A16-*.shp", encoding="cp932")

# WGS84に変換
gdf = gdf.to_crs(epsg=4326)

# 簡略化（オプション）
gdf['geometry'] = gdf['geometry'].simplify(0.0001, preserve_topology=True)

# GeoJSONとして保存
gdf.to_file("did_japan.geojson", driver="GeoJSON")
```

---

## 2. 空港等周辺の飛行禁止区域

### データソース
- 国土交通省航空局: https://www.mlit.go.jp/koku/koku_tk2_000023.html
- 各空港のウェブサイト

### 注意
このデータはAPIやダウンロード形式で提供されていないため、
手動でデジタル化する必要があります。

### 対象空港（小型無人機等飛行禁止法に基づく指定空港）
- 成田国際空港
- 東京国際空港（羽田）
- 中部国際空港
- 関西国際空港
- 大阪国際空港（伊丹）
- 福岡空港
- 新千歳空港
- 那覇空港
- その他（随時追加）

### GeoJSON形式

```json
{
  "type": "FeatureCollection",
  "features": [
    {
      "type": "Feature",
      "properties": {
        "name": "成田国際空港周辺制限区域",
        "airport_code": "NRT",
        "restriction_type": "no_fly"
      },
      "geometry": {
        "type": "Polygon",
        "coordinates": [[[経度, 緯度], ...]]
      }
    }
  ]
}
```

---

## 3. 小型無人機等飛行禁止区域

### データソース
- 警察庁: https://www.npa.go.jp/bureau/security/kogatamujinki/shitei.html
- 各省庁の指定施設リスト

### 対象施設
- 国会議事堂
- 内閣総理大臣官邸
- 皇居・御所
- 最高裁判所
- 原子力事業所
- 外国公館等
- その他重要施設

### 制限範囲
各施設の周囲おおむね300メートルの上空

### GeoJSON形式

```json
{
  "type": "FeatureCollection",
  "features": [
    {
      "type": "Feature",
      "properties": {
        "name": "国会議事堂周辺",
        "facility_type": "government",
        "radius_meters": 300
      },
      "geometry": {
        "type": "Polygon",
        "coordinates": [[[経度, 緯度], ...]]
      }
    }
  ]
}
```

---

## 法的注意事項

⚠️ **重要**

- このデータはドローン飛行の参考情報として使用してください
- 実際の飛行前には、必ず最新の法令・規制を確認してください
- データの正確性は保証されません。飛行の可否は各自の責任で判断してください
- 法改正や区域変更により、データが古くなる可能性があります

### 関連法令
- 航空法（昭和27年法律第231号）
- 小型無人機等飛行禁止法（平成28年法律第9号）

### 飛行申請・許可
- DIPS（ドローン情報基盤システム）: https://www.ossportal.dips.mlit.go.jp/


