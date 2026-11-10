# Implementation plan: Tiến độ mục tiêu & Nhật ký Điểm yếu loading

Ngày lập: 2026-10-05. Base: `integration/sprint2-1` sau khi merge Kanban và required-step completion fixes ở cả backend và frontend.

Trạng thái: kế hoạch triển khai; chưa thay đổi hành vi loading của ứng dụng.

## 1. Vấn đề đã xác minh

Frontend `src/app/(app)/knowledge-map/page.tsx` gọi Knowledge Map và Weak Topic Timeline trong cùng `Promise.all`. State `mapData.loading || timelineData.loading` khiến nội dung tab đang mở phải chờ cả API của tab chưa mở. Một lỗi Timeline cũng làm Knowledge Map hiển thị lỗi mặc dù request Knowledge Map đã thành công.

Khi response Knowledge Map trả Roadmap mặc định, trang gọi `setSelectedRoadmapId`. Thay đổi dependency của effect khiến cả hai report có thể được tải lần thứ hai. Request cũ không được hủy hoặc kiểm tra scope trước khi ghi state, nên đổi Roadmap nhanh có thể hiển thị kết quả của lựa chọn trước.

Cả hai view đang được import trực tiếp. Knowledge Map mở mọi Milestone mặc định, dù Learning Units bên trong Topic chỉ được mount khi Topic mở. Timeline filter, sort và render toàn bộ lịch sử trên trình duyệt.

Backend hiện trả toàn bộ hierarchy của ACTIVE RoadmapVersion cho Knowledge Map và toàn bộ Weak Topic history phù hợp scope cho Timeline. Timeline chưa hỗ trợ pagination. Lazy loading theo tab sẽ giảm công việc lúc mở trang, nhưng chưa giảm kích thước từng response.

## 2. Kết quả mong muốn

- Tab bar và Roadmap selector hiển thị ngay; loading chỉ xuất hiện trong vùng nội dung cần dữ liệu.
- Mở trang chỉ tải Knowledge Map và danh sách Roadmap phục vụ selector. Chưa tải Timeline hoặc bundle của Timeline trước lần mở tab đó.
- Knowledge Map hiển thị ngay khi API của nó hoàn tất.
- Mở Timeline lần đầu mới gọi API Timeline. Chuyển tab trở lại dùng dữ liệu đã tải của đúng Roadmap.
- Loading, lỗi, retry và refresh của mỗi tab độc lập.
- Đổi Roadmap chỉ tải tab đang mở; response cũ không ghi đè lựa chọn mới.
- Kết quả tổng hợp vẫn phản ánh toàn bộ dữ liệu trong scope, kể cả khi danh sách sau này được phân trang.

## 3. PR1 — Frontend: tải dữ liệu theo tab và quản lý request

### Files

- `D:/ai-frontend/src/app/(app)/knowledge-map/page.tsx`
- `D:/ai-frontend/src/features/reports/report-api.ts`
- Hook mới trong `src/features/reports/knowledge-map/` để quản lý report theo tab và Roadmap.
- Regression tests cho page/hook.

### Implementation

1. Tách request Knowledge Map và Timeline. Mỗi resource có `idle`, `loading`, `success`, `error`, cùng dữ liệu và thời điểm tải.
2. Dùng cache trong vòng đời trang, key theo report type và Roadmap scope. Không chia sẻ cache giữa tài khoản. Dùng trạng thái resource để phân biệt Timeline đã tải nhưng rỗng với Timeline chưa tải.
3. Effect chỉ yêu cầu resource của tab đang mở khi chưa có dữ liệu. Resource đang pending được dùng lại để tránh request trùng. Không thêm polling.
4. Chỉ dùng loading/error của tab đang mở để render và điều khiển nút Làm mới. Refresh giữ dữ liệu cùng scope hiện tại trong khi tải và hiển thị trạng thái cập nhật nhỏ; retry chỉ gọi API thất bại của tab đó.
5. Tách lựa chọn `default` khỏi `resolvedRoadmapId`. Nhận ID mặc định từ Knowledge Map để đặt scope cho Timeline mà không trigger tải lại Knowledge Map. Cache response mặc định dưới cả key default và ID đã resolve khi phù hợp.
6. Theo nhãn "Lộ trình hiện tại / Mặc định", Timeline dùng ID Roadmap đã resolve. Không gửi `roadmapId` rỗng để vô tình lấy lịch sử của mọi Roadmap khi người dùng đang xem một Roadmap. Nếu chưa resolve hoặc không có Roadmap, hiển thị empty/loading state tương ứng. Mọi lựa chọn "Tất cả lộ trình" trong tương lai phải là scope riêng, rõ ràng.
7. Cho các report API helper nhận `AbortSignal` tùy chọn; `apiRequest` đã nhận `RequestInit`. Hủy request không còn cần khi đổi scope hoặc unmount, đồng thời kiểm tra request identity/generation trước khi ghi cache và state. Không hiển thị AbortError như lỗi người dùng.
8. Clear hoặc bỏ cache khi auth scope đổi. Re-enter trang tải dữ liệu mới; Làm mới cho phép người dùng lấy số liệu mới trong cùng phiên xem. Không cache vô hạn trên module/global state.

### Acceptance criteria / verification

- Mở trang: một Knowledge Map request logic; zero Timeline request trước khi mở Timeline. React development Strict Mode không tạo hai request sống đồng thời cho cùng resource.
- Knowledge Map thành công vẫn hiển thị khi Timeline chậm hoặc lỗi.
- Lần mở Timeline đầu tải một request; mở lại Timeline đã tải, kể cả kết quả rỗng, không tải lại.
- ID Roadmap mặc định không gây fetch Knowledge Map lần hai.
- Đổi A sang B khi A pending không hiển thị response A trong scope B.
- Làm mới/Thử lại chỉ tác động tab hiện tại; hủy request không hiện error banner.
- Test bằng deferred promises để chứng minh kết quả đầu tiên render trước khi request khác hoàn tất. Chạy focused tests, type/build và lint.

## 4. PR2 — Frontend: tải component và render hierarchy khi cần

Phụ thuộc PR1. Không yêu cầu thay đổi API backend.

1. Dùng `next/dynamic` cho Timeline; chỉ mount khi tab đó mở. Skeleton cho bundle loading và data loading dùng cùng bố cục để hạn chế layout shift.
2. Knowledge Map vẫn render summary khi report của nó sẵn sàng. Chỉ mở Milestone đầu tiên mặc định; các Milestone khác mount danh sách Topic khi người dùng mở. Learning Units tiếp tục chỉ mount khi Topic mở.
3. Khi người dùng tìm kiếm, tự mở các nhánh có kết quả để không giấu match trong nhánh đóng. Clear search khôi phục trạng thái mở/đóng người dùng đã chọn. Giữ filter hoạt động trên toàn bộ dữ liệu đã tải, không chỉ nhánh đang mở.
4. Key view theo Roadmap scope hoặc reset expansion/filter cần thiết để trạng thái Roadmap A không lẫn sang B. Giữ trạng thái filter/expansion khi chuyển tab trong cùng Roadmap bằng state bên ngoài view hoặc mount đã được kích hoạt; không render cây chưa được mở.
5. Skeleton có nhãn phù hợp từng tab, `aria-busy`, vùng báo lỗi độc lập và retry bằng bàn phím. Selector có accessible label và trạng thái loading riêng; lỗi selector không khóa report.

### Acceptance criteria / verification

- Chưa mở Timeline thì không mount Timeline view.
- Milestone đóng không mount Topic/Unit rows; mở nhánh hiển thị đúng dữ liệu.
- Search thấy kết quả trong Milestone vốn đóng và số liệu summary không bị tính lại theo kết quả lọc.
- Kiểm tra slow-network, mobile width và chuyển tab nhanh. Đo Network/React Profiler trước và sau bằng Roadmap có nhiều Topic.

## 5. PR3 — Backend + frontend: phân trang Timeline khi history lớn

Phụ thuộc PR1/PR2. Triển khai sau khi đo cho thấy request Timeline hoặc render history vẫn chậm; đây không phải dependency của fix loading ban đầu.

### Backend

1. Bổ sung endpoint phân trang Timeline bên cạnh endpoint `List` hiện tại để giữ client cũ hoạt động. Nhận `roadmapId`, status filter, page và size; mặc định 20, giới hạn 100.
2. Trả `PageResponse` với content, paging metadata và summary toàn scope: mastered count, in-review count, unresolved count, average days-to-master. Summary không được tính từ một page và giữ đúng timezone của từng Weak Topic.
3. Filter/sort nằm trên server. Dùng thứ tự event gần nhất giảm dần với ID làm tie-breaker để pagination ổn định; thống nhất thứ tự với UI hiện tại trước khi bỏ client-side sort.
4. Query luôn owner-scoped. Dùng count/aggregate query và fetch context theo page IDs hoặc projection để tránh phân trang collection fetch/N+1. Giữ lịch sử theo Roadmap scope hiện tại; không tự đổi sang chỉ ACTIVE version.
5. Không thay database entity/schema nếu query hiện có đáp ứng. Chỉ thêm index bằng migration mới nếu measurement/query plan chứng minh cần.

### Frontend

1. Chuyển Timeline sang endpoint mới, tải page đầu khi mở tab và tải page tiếp khi bấm "Tải thêm". Không yêu cầu infinite scroll cho lần triển khai đầu.
2. Summary cards/tab count dùng summary toàn scope và totalElements, không dùng `loadedItems.length`.
3. Đổi filter/scope reset page và list. Request cũ không append vào list mới; deduplicate item bằng weakTopicId. Retry page sau giữ các page đã tải.
4. Không sort lại từng page trên client. Empty state chỉ xuất hiện khi server xác nhận totalElements bằng zero.

### Acceptance criteria / verification

- Timeline có trên 20 bản ghi chỉ tải page đầu khi mở; "Tải thêm" lấy page tiếp mà không nhân đôi items.
- Summary/count không đổi sai theo số page được tải hoặc bộ lọc danh sách.
- Owner A không thể đọc history của Owner B; status filter và order có tie-breaker đúng.
- Test pagination, empty, retry và đổi filter khi page request đang pending.

## 6. Knowledge Map payload lớn: bước tối ưu tiếp theo

PR1/PR2 vẫn cần toàn bộ Knowledge Map response để giữ full-data search/filter và summary chính xác. Nếu measurement sau hai PR này vẫn cho thấy endpoint này là bottleneck, tách summary/Milestone headers khỏi Topic/Unit details và tải từng Milestone khi mở. Search khi đó cần server-side support để không chỉ tìm trong nhánh đã tải. Đây là bước riêng, không gộp vào fix loading đầu tiên.

Roadmap selector hiện lấy tối đa 50 summaries. Trong PR1 giữ contract hiện tại; việc tìm/chọn Roadmap ngoài page đầu sẽ cần selector search/pagination riêng nếu tài khoản có hơn 50 Roadmaps. Không lấy toàn bộ Roadmap details để populate selector.

## 7. Thứ tự thực hiện và bàn giao

1. Tạo frontend branch từ `integration/sprint2-1`, triển khai PR1 trước để loại bỏ dependency chờ tab chưa mở và request trùng.
2. Triển khai PR2, verify với slow-network và một Roadmap lớn. Ghi lại thời gian đến nội dung đầu tiên, số request và độ lớn payload thực tế; không hứa target latency trước khi đo.
3. Chỉ mở backend branch cho PR3 khi có nhu cầu phân trang Timeline. Bàn giao contract trước khi frontend chuyển endpoint.
4. Loading plan không thay đổi quy tắc completion, weakness/mastery, progress history hoặc Kanban execution. No automatic retries/polling cho reports.
