# 年調クエスト

年末調整の作業を冒険として管理する、Spring Boot製のタスク管理アプリケーションです。

## 先生向け：起動方法

### 必要なもの

- JDK 17（`pom.xml` の `java.version` に準拠）
- VS Code と Java 拡張機能

初回は、Mavenが必要なライブラリを取得するためネットワーク接続が必要になる場合があります。

### 起動手順

1. このリポジトリをGitHubから取得して、VS Codeで開きます。
2. `src/main/java/com/example/demo/DemoApplication.java` を開きます。
3. Javaの通常の **Run** を押します。
4. ブラウザで [http://localhost:8080](http://localhost:8080) を開きます。
5. 次のデモ用アカウントでログインします。

| 項目 | 値 |
| --- | --- |
| 冒険者名 | よりとも |
| 合言葉 | 1192 |

デモ起動は `localhost` にだけ待ち受け、MCPは無効です。デモ認証情報は公開可能な確認専用のものであり、通常運用には使用しません。アプリはH2のインメモリデータベースを利用するため、停止すると登録内容は初期状態に戻ります。

## 実装済み機能

- ログイン、ログアウト、CSRF保護、BCryptによるパスワードハッシュ化
- タスクの登録、確認、編集、削除、完了・未完了の切替
- 親子タスク、カテゴリー、担当者、検索・絞り込み、期限順表示、並べ替え
- ダッシュボード、カンバン、ガントチャート、カレンダー
- 期限アラート、一括ステータス変更・一括削除（確認画面付き）
- CSVのエクスポート、テンプレート配布、内容確認付きインポート

## REST API

ブラウザ画面とは別に、認証が必要なREST APIを提供します。書き込みを含むリクエストでは、ログイン済みセッションとCSRF保護が必要です。

| 操作 | エンドポイント |
| --- | --- |
| タスク一覧・詳細 | `GET /api/todos`、`GET /api/todos/{id}` |
| タスク登録 | `POST /api/todos` |
| タスク更新・削除 | `PUT /api/todos/{id}`、`DELETE /api/todos/{id}` |
| ステータス更新 | `PUT /api/todo/{id}/status` |
| 表示順更新 | `PUT /api/todo/reorder` |

## 日本の祝日情報

カレンダーと空き営業日の判定には、[Holidays JP](https://holidays-jp.github.io/) の祝日APIを利用します。取得できない年は祝日不明として扱い、通常の画面利用を止めない設計です。

## 任意：ローカルMCP連携

通常の起動・提出確認にMCP設定は不要です。開発者がローカルのMCP連携を使う場合だけ、既存の `.env` に `SPRING_PROFILES_ACTIVE=mcp-local`、冒険者名・合言葉、MCPアクセストークンを設定します。`.env` はGit管理対象外です。

`mcp-local` はlocalhost限定で、専用Bearerトークンを要求します。提供するツールはすべて読み取り専用です。

- `list_todos`
- `get_todo`
- `find_free_days`
- `summarize_week`

## 自動テスト

JUnitによる自動テストを用意しています。タスク画面・REST API・CSV入出力・期限表示・カレンダー・並べ替え・MCPツールに加え、デモモードがlocalhost限定かつMCP無効であることを検証します。

```powershell
.\mvnw.cmd test
```

テスト実行時に外部の実際の認証情報は使用しません。
