# USKB-01 — Kế hoạch sửa luồng Kanban và Daily Micro-Quiz

## 1. Trạng thái tài liệu

Tài liệu này mô tả những thay đổi cần thực hiện trên hai nhánh
`feature/USKB-01` của Backend và Frontend trước khi có thể merge vào
`integration/sprint2-1`.

Đây là tài liệu sửa sai và làm rõ nghiệp vụ dựa trên xác nhận của người thực
hiện tính năng. Tài liệu không tự động thay thế các đặc tả đã được Product
Owner phê duyệt. Những điểm thay đổi so với `task-step-decomposition.md` phải
được team hoặc Product Owner xác nhận trước khi coi là quy tắc chính thức.

## 2. Ý định nghiệp vụ đã được xác nhận

- Đơn vị kéo thả trên Kanban là `DailyPlanItem`.
- Một `DailyPlanItem` được coi là một nhiệm vụ học tập hoàn chỉnh.
- Các Task Step bên trong là hướng dẫn giúp hoàn thành nhiệm vụ cha; USKB-01
  không bắt buộc USER phải đánh dấu từng step trước khi xử lý nhiệm vụ cha.
- Kanban có bốn cột:
  - `NOT_STARTED`: Chưa hoàn thành.
  - `IN_PROGRESS`: Đang thực hiện.
  - `REVIEWING`: Đang chờ kiểm tra cuối ngày.
  - `COMPLETED`: Hoàn thành.
- Daily Micro-Quiz thuộc về một `DailyPlanVersion`, không thuộc riêng một
  `DailyPlanItem`.
- Mastery Check của Weak Topic là luồng độc lập, thuộc về đúng một Weak Topic
  và một Learning Unit.
- Điểm đạt Daily Micro-Quiz là từ 80% trở lên.
- Chỉ task thuộc `ACTIVE DailyPlanVersion`, khi `DailyPlan` ở `READY` hoặc
  `IN_PROGRESS`, mới được thay đổi trạng thái thực hiện.
- USKB-01 chỉ bao gồm bảng Kanban và luồng trạng thái liên quan. Dashboard
  history, Create Plan Modal và thay đổi ngoài màn hình chi tiết Daily Plan
  không thuộc phạm vi này.

## 3. Vấn đề trong implementation hiện tại

### 3.1. `REVIEWING` bị tính sai thành 75% hoàn thành

Không có Business Rule quy định `REVIEWING = 75%`. Đây chỉ là giá trị nội suy
kỹ thuật. Nó làm các phép tính mâu thuẫn:

- phần trăm Daily Plan tăng 75%;
- số task hoàn thành không tăng;
- Roadmap progress không tăng;
- AI ngày sau vẫn coi task là chưa hoàn tất;
- Micro-Quiz lại coi task là đủ điều kiện làm nguồn kiểm tra.

`REVIEWING` phải được hiểu là trạng thái workflow "đã học xong và đang chờ
kiểm tra", không phải một kết quả tiến độ lịch sử.

### 3.2. Một kết quả quiz toàn phiên bản đang mở khóa mọi task

Frontend hiện truyền một biến `quizPassed` cấp Daily Plan cho tất cả card.
Sau khi USER vượt qua một quiz, mọi task ở `REVIEWING` đều có thể chuyển sang
`COMPLETED`, kể cả task được đưa vào `REVIEWING` sau khi quiz đã được sinh.

Ví dụ lỗi:

1. Task A vào `REVIEWING`.
2. Quiz được sinh chỉ từ Task A và USER vượt qua.
3. Task B vào `REVIEWING` sau đó.
4. `quizPassed` cũ mở khóa Task B dù Task B chưa từng nằm trong quiz.

Hệ thống phải lưu snapshot chính xác các task/Learning Unit đã được dùng để
sinh từng quiz.

### 3.3. Quiz được sinh quá sớm

Việc tự mở và sinh quiz ngay khi task đầu tiên vào `REVIEWING` khiến quiz có
thể bỏ sót các task USER chưa kịp hoàn thành trong ngày. Quiz nên được bắt đầu
bằng một hành động rõ ràng ở cấp Daily Plan Version sau khi USER đã đưa các
task cần kiểm tra vào `REVIEWING`.

### 3.4. AI failure có thể làm USER bị kẹt

Nếu quiz là điều kiện duy nhất để sang `COMPLETED`, lỗi provider, timeout,
quota hoặc output không hợp lệ sẽ khiến USER không thể hoàn thành Daily Plan.
Điều này vi phạm nguyên tắc manual workflow phải tiếp tục hoạt động khi AI
không khả dụng.

### 3.5. Trạng thái của DRAFT và SUPERSEDED vẫn có thể bị thay đổi

Backend hiện chỉ dùng kiểm tra active version để quyết định có tạo
`ProgressEntry` hay không, nhưng vẫn lưu status cho task của version không
active. Frontend vẫn hiển thị các nút chuyển cột trên version không executable.

### 3.6. Hoàn thành nhanh bỏ qua dữ liệu tiến độ chuẩn

Kéo card sang `COMPLETED` đang dùng endpoint cập nhật status trực tiếp, tự lấy
`plannedMinutes` làm `actualMinutes`, không có idempotency và không thu thập
actual result, difficulty hoặc understanding rating. Hành vi này có thể tạo
history trùng lặp và làm giảm chất lượng dữ liệu Weak Topic/AI planning.

### 3.7. Mở lại task hoàn thành không dùng correction

Kéo task từ `COMPLETED` về `IN_PROGRESS` hoặc `REVIEWING` chỉ đổi snapshot của
`DailyPlanItem`. `ProgressEntry` và Roadmap progress cũ vẫn giữ `COMPLETED`.
Endpoint correction hiện có nhưng chưa được Kanban sử dụng.

### 3.8. `PARTIALLY_COMPLETED` và `SKIPPED` có nguy cơ bị ghi đè

Frontend đang hiển thị `PARTIALLY_COMPLETED` trong cột `IN_PROGRESS` và
`SKIPPED` trong cột `NOT_STARTED`. Việc nhóm để hiển thị là chấp nhận được,
nhưng thả card trở lại chính cột đó không được đổi outcome thành
`IN_PROGRESS` hoặc `NOT_STARTED`.

### 3.9. Scope creep trong Frontend PR

Nhánh hiện tại đồng thời thay đổi Dashboard, Daily Plan history, Create Plan
Modal và cơ chế tự sinh Micro-Quiz. Các thay đổi này không thuộc AC của
USKB-01, làm tăng rủi ro merge và hiện còn gây lỗi lint.

## 4. Mô hình nghiệp vụ mục tiêu

### 4.1. State machine của task

```text
NOT_STARTED --USER bắt đầu------------------------> IN_PROGRESS
IN_PROGRESS --USER xác nhận đã sẵn sàng kiểm tra--> REVIEWING
REVIEWING   --USER cần học tiếp--------------------> IN_PROGRESS
REVIEWING   --quiz hợp lệ hoặc manual fallback----> COMPLETED
```

Quy tắc:

- Không cho phép bỏ qua trực tiếp các điều kiện từ `NOT_STARTED` sang
  `COMPLETED` bằng endpoint status thông thường.
- `COMPLETED`, `PARTIALLY_COMPLETED` và `SKIPPED` là outcome có lịch sử, không
  chỉ là vị trí UI.
- Mọi thay đổi một outcome đã ghi nhận phải đi qua correction append-only.
- Chuyển cột không được xóa hoặc sửa ngầm `ProgressEntry` cũ.

### 4.2. Ý nghĩa phần trăm

Phần trăm hoàn thành phải phản ánh outcome thực tế:

| Trạng thái | Tỷ lệ đóng góp đề xuất |
|---|---:|
| `NOT_STARTED` | 0% |
| `IN_PROGRESS` | 0% |
| `REVIEWING` | 0% |
| `PARTIALLY_COMPLETED` | 50% |
| `SKIPPED` | 0% |
| `COMPLETED` | 100% |

UI có thể hiển thị riêng số task theo từng giai đoạn để USER thấy tiến trình
workflow. Không được dùng một tỷ lệ không có Business Rule để thay thế dữ liệu
outcome.

### 4.3. Review batch và phạm vi quiz

Daily Micro-Quiz phải kiểm tra một snapshot bất biến của các task đang
`REVIEWING` tại thời điểm USER bắt đầu kiểm tra.

Luồng đề xuất:

1. USER đưa một hoặc nhiều task vào `REVIEWING`.
2. UI hiển thị số task đang chờ kiểm tra.
3. USER bấm `Bắt đầu kiểm tra cuối ngày`.
4. Backend khóa đúng `ACTIVE DailyPlanVersion` và thu thập các task đủ điều
   kiện.
5. Backend lưu snapshot task ID và Learning Unit ID của review batch.
6. AI sinh một Daily Micro-Quiz cho review batch đó.
7. Kết quả từ 80% trở lên chỉ mở khóa các task nằm trong snapshot.
8. USER chủ động chuyển các task đã được mở khóa sang `COMPLETED`.
9. Task vào `REVIEWING` sau thời điểm snapshot cần review batch/quiz tiếp theo.

Quiz pass không tự động đánh dấu task hoàn thành. USER vẫn là chủ thể xác nhận
outcome cuối cùng.

### 4.4. Task đủ điều kiện đưa vào quiz

Một task chỉ được đưa vào Daily Micro-Quiz khi:

- thuộc đúng USER;
- thuộc đúng `ACTIVE DailyPlanVersion`;
- đang ở `REVIEWING`;
- có liên kết Learning Unit hợp lệ thuộc Roadmap của Daily Plan;
- chưa được một quiz pass còn hiệu lực bao phủ.

Task `CUSTOM` hoặc task không có Learning Unit không được làm hỏng toàn bộ
review batch. Nó phải có luồng hoàn thành thủ công.

### 4.5. Manual fallback

Khi AI không khả dụng hoặc task không đủ điều kiện sinh quiz, UI phải cho phép
USER chọn `Hoàn thành thủ công`.

Manual fallback phải:

- yêu cầu xác nhận rõ ràng;
- tạo `ProgressEntry` bình thường;
- dùng reason code kỹ thuật như `AI_UNAVAILABLE`, `TASK_NOT_QUIZ_ELIGIBLE` hoặc
  `USER_MANUAL_OVERRIDE`;
- chỉ ghi ID và reason code vào audit metadata, không ghi nội dung học tập;
- không tự động đánh dấu Weak Topic là `MASTERED`.

### 4.6. Task Steps

Trong phạm vi USKB-01:

- Task Step có thể hiển thị dưới dạng accordion hướng dẫn.
- USER không bắt buộc hoàn thành từng step để chuyển task cha.
- Không xóa schema, API hoặc lịch sử completion của Task Step.
- Việc bỏ hoàn toàn checkbox khỏi sản phẩm phải được cập nhật riêng trong
  `task-step-decomposition.md` sau khi Product Owner/team phê duyệt.
- Kanban không được làm mất khả năng mở màn hình chi tiết task nếu chức năng
  này vẫn được sản phẩm giữ lại.

## 5. Thay đổi Backend cần thực hiện

### 5.1. Domain và validation

- Giữ `DailyTaskStatus.REVIEWING` nếu quy tắc bốn cột được phê duyệt.
- Bỏ giá trị `75` trong `completionPercentage()`; `REVIEWING` không đóng góp
  completion outcome.
- Định nghĩa transition validator thay vì chấp nhận mọi enum value.
- Tách status movement khỏi outcome recording trong service.
- Chỉ chấp nhận movement cho exact ACTIVE version và plan `READY` hoặc
  `IN_PROGRESS`.
- Từ chối mutation đối với DRAFT, SUPERSEDED, COMPLETED hoặc CANCELLED.
- Giữ owner scope ở mọi truy vấn plan, version, item, quiz và review scope.

### 5.2. Progress và correction

- `NOT_STARTED`, `IN_PROGRESS`, `REVIEWING` chỉ thay đổi workflow status và
  không tạo `ProgressEntry` hoàn thành.
- `COMPLETED`, `PARTIALLY_COMPLETED`, `SKIPPED` phải đi qua progress service
  hiện có.
- Quick completion phải có idempotency key.
- Nếu dùng `plannedMinutes` làm mặc định, API/UI phải thông báo rõ và cho phép
  USER sửa sau bằng correction.
- Mở lại task đã có terminal outcome phải append correction và rebuild snapshot
  Roadmap; không được cập nhật status đơn thuần.

### 5.3. Quiz scope

- Không dùng một `quizPassed` cấp version làm quyền mở khóa vô thời hạn cho mọi
  task.
- Persist association giữa quiz/review batch và các `DailyPlanItem` hoặc
  Learning Unit đã được đưa vào prompt.
- Quiz detail/result phải trả về danh sách ID được bao phủ; không trả nội dung
  prompt hoặc dữ liệu cá nhân không cần thiết.
- Khi kiểm tra transition sang `COMPLETED`, backend phải xác nhận task thuộc
  phạm vi của một quiz pass còn hiệu lực hoặc dùng manual fallback hợp lệ.
- `DailyEvaluationPersistenceService` chỉ lấy task `REVIEWING` của exact active
  version và phải lưu phạm vi đã lấy.

Nếu V27 đã được chia sẻ hoặc apply ở bất kỳ môi trường chung nào, không sửa
V27. Mọi schema bổ sung cho review scope phải bắt đầu từ migration mới sau
version hiện tại.

### 5.4. API contract

Endpoint status hiện tại có thể tiếp tục phục vụ transition không-terminal,
nhưng request nên có:

- target status;
- expected entity version để chống stale update;
- idempotency key đối với thao tác có side effect.

API Daily Micro-Quiz cần cho phép:

- tạo/recover review batch của exact Daily Plan Version;
- trả trạng thái generation bất đồng bộ;
- trả danh sách task ID nằm trong review scope;
- submit quiz và trả pass/fail cùng scope đã được mở khóa;
- không tạo batch rỗng hoặc batch chứa resource khác owner/version.

### 5.5. Audit

- Dùng audit action riêng như `DAILY_TASK_STATUS_CHANGED` hoặc
  `KANBAN_TASK_MOVED` cho chuyển trạng thái workflow.
- Chỉ dùng `PROGRESS_RECORDED` khi thật sự tạo outcome history.
- Có audit event cho review batch queued, passed, failed và manual override.
- Không ghi task title, câu hỏi, câu trả lời, prompt hoặc provider output vào
  log/audit metadata.

## 6. Thay đổi Frontend cần thực hiện

### 6.1. Kanban board

- Giữ bố cục bốn cột và phần xử lý drag-state hiện tại.
- Chỉ bật drag và quick action khi version đang chọn là ACTIVE và plan ở
  `READY` hoặc `IN_PROGRESS`.
- DRAFT chỉ được sửa nội dung; SUPERSEDED chỉ đọc.
- Disable card trong khi request của chính card đang chạy.
- Khi backend từ chối optimistic-lock hoặc transition, rollback UI về response
  authoritative.
- Nếu trong cùng một cột không hỗ trợ reorder thì không phát request status.

### 6.2. Review và quiz

- Không tự mở quiz ngay khi task đầu tiên vào `REVIEWING`.
- Thêm CTA cấp board: `Bắt đầu kiểm tra cuối ngày`.
- Hiển thị rõ task nào đang chờ, đang nằm trong quiz hiện tại, đã được quiz pass
  mở khóa, hoặc chưa được kiểm tra.
- Không truyền một boolean `quizPassed` chung như quyền mở khóa cho mọi card.
- Quiz modal phải dùng đúng Daily Plan Version đang executable và đúng review
  batch, không dùng version khác đang được xem.
- Khi generation thất bại, hiển thị retry và manual fallback; không khóa USER.

### 6.3. Outcome

- Chuyển sang `COMPLETED` phải gọi progress flow, không chỉ PATCH status.
- Cho phép quick complete với thông tin mặc định đã công bố hoặc mở
  `ProgressModal` để nhập đầy đủ.
- Chuyển một terminal outcome về trạng thái trước phải mở confirmation và gọi
  correction API.
- `PARTIALLY_COMPLETED` và `SKIPPED` phải giữ badge/outcome gốc dù được nhóm
  vào một lane để hiển thị.

### 6.4. Task Steps

- Accordion hướng dẫn có thể giữ lại theo ý định mới.
- Không xóa dữ liệu step hoặc giả định API checkbox đã bị loại khỏi backend.
- Nếu team vẫn giữ checklist tùy chọn, card phải có action mở Task Step dialog.
- Quyết định thay đổi Task Step phải được tách khỏi PR USKB-01 nếu chưa được PO
  phê duyệt.

### 6.5. Thu hẹp phạm vi PR

Tách hoặc revert khỏi USKB-01:

- `DailyPlansHistoryStrip` trên Dashboard;
- refactor Create Plan Modal;
- thay đổi ngoài luồng Kanban trong Micro-Quiz modal;
- mọi thay đổi UI không cần thiết để chạy state machine đã xác nhận.

## 7. Migration và tương thích dữ liệu

- Không sửa migration đã được apply trên môi trường dùng chung.
- Nếu `REVIEWING` được giữ, constraint phải tiếp tục cho phép giá trị này.
- Nếu cần lưu review-batch scope, tạo migration mới với foreign key tới exact
  Daily Plan Version, Quiz và Daily Plan Item.
- Association phải ngăn liên kết item thuộc version khác.
- Không cascade-delete ProgressEntry hoặc quiz history khi task/version được
  archive.
- Existing tasks và quizzes không có review scope vẫn đọc được; chúng không
  được dùng để tự động mở khóa task mới.

## 8. Test bắt buộc

### Backend

- Owner USER chuyển đúng state trên ACTIVE version.
- USER khác nhận `RESOURCE_NOT_FOUND`; ADMIN nhận `403`.
- DRAFT và SUPERSEDED không thể thực thi.
- Plan COMPLETED/CANCELLED không thể đổi task status.
- Transition không hợp lệ bị từ chối.
- `REVIEWING` không làm tăng completion percentage hoặc Roadmap progress.
- Review batch snapshot đúng tập task tại thời điểm generation.
- Task vào `REVIEWING` sau đó không được quiz cũ mở khóa.
- Quiz pass chỉ mở khóa item nằm trong scope.
- Quiz fail không hoàn thành task.
- AI failure vẫn cho phép manual fallback.
- Quick completion idempotent, không tạo duplicate history.
- Reopen terminal outcome tạo correction và đồng bộ Roadmap snapshot.
- `PARTIALLY_COMPLETED` và `SKIPPED` không bị đổi ngầm.

### Frontend

- Thực sự mô phỏng drag/drop, không chỉ test quick buttons.
- DRAFT và SUPERSEDED không có control thực thi.
- CTA quiz chỉ xuất hiện khi có eligible `REVIEWING` task.
- Một quiz pass không mở khóa task ngoài scope.
- Loading, failure, retry và manual fallback hiển thị đúng.
- API failure rollback optimistic state.
- Card terminal mở correction thay vì status update.
- `PARTIALLY_COMPLETED` và `SKIPPED` giữ label chính xác.
- Task Steps hiển thị theo quyết định đã được phê duyệt.
- Keyboard và touch có thao tác thay thế drag/drop.

### Quality gate

- Backend full test suite pass.
- Frontend unit/component tests pass.
- Frontend production build pass.
- Frontend ESLint không có error mới.
- Không có migration version trùng.
- Không có nội dung học tập, quiz hoặc AI prompt trong log/audit metadata.

## 9. Acceptance Criteria sau khi sửa

1. USER xem được Kanban bốn cột của exact ACTIVE Daily Plan Version.
2. Task mới bắt đầu ở `NOT_STARTED`; USER có thể chuyển tuần tự sang
   `IN_PROGRESS` và `REVIEWING`.
3. `REVIEWING` không được tính là outcome hoàn thành hoặc 75% tiến độ.
4. USER chủ động bắt đầu một Daily Micro-Quiz cho snapshot các task đang
   `REVIEWING` và đủ điều kiện.
5. Quiz đạt từ 80% chỉ mở khóa các task nằm trong snapshot đó.
6. Task ngoài quiz scope không được dùng kết quả quiz cũ để sang `COMPLETED`.
7. Task không quiz-eligible hoặc lúc AI unavailable vẫn có manual completion
   được xác nhận và audit.
8. Chuyển sang `COMPLETED` tạo đúng một ProgressEntry và cập nhật Roadmap
   progress khi có Learning Unit.
9. Sửa hoặc mở lại outcome dùng correction, không phá lịch sử.
10. DRAFT, SUPERSEDED và terminal Daily Plan không thể thực thi qua Kanban.
11. `PARTIALLY_COMPLETED` và `SKIPPED` không bị ghi đè bởi cách nhóm cột UI.
12. Task Steps vẫn được bảo toàn; cách hiển thị checkbox hay guidance tuân theo
    quyết định sản phẩm riêng.
13. ADMIN không thể truy cập Kanban, quiz hoặc progress cá nhân.
14. Dashboard history và Create Plan refactor không nằm trong PR USKB-01.

## 10. Trình tự sửa đề xuất

1. Chốt và cập nhật Business Rule về `REVIEWING`, Task Step và manual fallback.
2. Sửa state transition, active-version guard và progress semantics ở Backend.
3. Bổ sung review-batch scope và migration mới nếu cần.
4. Hoàn thiện quiz generation/submission theo scope bất biến.
5. Viết đầy đủ Backend integration tests.
6. Refactor Frontend dùng contract mới và bỏ global `quizPassed`.
7. Tách các thay đổi ngoài USKB-01 khỏi PR.
8. Hoàn thiện drag/drop, accessibility và failure states.
9. Chạy toàn bộ test, lint, build và kiểm tra migration trên database sạch.
10. Chỉ merge khi tất cả Acceptance Criteria và quality gate đều đạt.

## 11. Ngoài phạm vi

- Mastery Check riêng của Weak Topic.
- Thay đổi ngưỡng mastery.
- Knowledge map hoặc mastery engine mới.
- Dashboard history redesign.
- Create Plan workflow redesign.
- Reorder card trong cùng một cột, trừ khi được thêm thành AC riêng.
- Xóa schema hoặc lịch sử Task Step hiện có.
