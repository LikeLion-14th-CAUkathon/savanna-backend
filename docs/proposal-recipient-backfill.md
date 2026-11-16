# 배포 전 합류한 팀원의 제안 수신 기록 보충

## 원인과 범위

TeamInvitationService.acceptInvitation의 registerExistingProposals는 초대 수락 시에만 실행합니다. 배포 전에 이미 소속돼 있던 팀원은 다시 합류하지 않는 한 누락된 ProposalRecipient가 생성되지 않습니다. 구글 계정의 신규/기존 여부 자체가 판정 조건은 아닙니다.

일회성 데이터 보충 SQL을 준비했습니다. 새 API나 로그인 시 자동 쓰기를 추가하지 않습니다. EC2 및 실제 로컬 DB에는 적용하지 않았습니다.

- 현재 team_member 소속과 같은 팀의 제안만 대상입니다. 탈퇴자·다른 팀 소속은 추가하지 않습니다.
- Asia/Seoul 기준 오늘을 포함하여 deadline >= 오늘인 공고만 대상입니다. 날짜 단위 정책상 마감 당일까지 포함하며, 마감/마감일 미확인은 제외합니다.
- submission_completed_at IS NULL이며 stage != SUBMITTED인 원정만 대상입니다. 제출 상태와 제출시각이 불일치한 SUBMITTED 행도 추가하지 않습니다.
- 발신자는 수신자가 아니므로 제외합니다.
- (proposal_id, user_id)가 없는 경우에만 PENDING으로 생성합니다. 기존 JOINED/DECLINED/PENDING 및 모든 읽음·응답 시각/버전은 변경하지 않습니다.
- 새 행은 read_at/responded_at/response_read_at=null, response_version/response_read_version=0입니다. 받은 미응답 수와 읽지 않은 새 제안 수가 늘어날 수 있으며 정상 동작입니다. 발신자의 새 응답 알림은 실제 응답 전에는 생성되지 않습니다.
- 가입된 팀원을 원정에 자동 JOINED 처리하지 않습니다. 사용자가 기존 응답 API에서 직접 선택합니다.
- NOT EXISTS로 재실행은 무변경입니다. unique(proposal_id,user_id) 제약도 유지합니다. 실행 중 쓰기는 서비스 정상 중지로 방지합니다.

## EC2 적용 순서 (직접 적용하지 않음)

1. H2에 미확정한 다른 트랜잭션이 있다면 먼저 해당 작업을 검증하고 COMMIT 또는 ROLLBACK 후 quit합니다. 작업을 섞지 않습니다.
2. Windows Git Bash에서 preview-missing-proposal-recipients.sql과 backfill-missing-proposal-recipients.sql을 EC2에 전송할 수 있습니다. 파일을 서버에서 SQL RUNSCRIPT로 실행하지는 않습니다. 로컬 편집기에서 내용을 복사해 H2 Shell sql>에 직접 붙여 넣습니다.
3. EC2에서 서비스 정상 중지, MainPID=0 및 DB 점유 없음 확인 후 현재 DB를 새로 백업합니다. 다른 프로세스 점유가 있으면 강제 종료하지 않습니다.
4. 기존 운영 DB에 IFEXISTS=TRUE로 H2 Shell 접속하고 preview SQL을 실행합니다. 결과의 팀/원정/제안/user_id를 확인합니다. 0건이면 보충 대상이 없는 것이며 조건을 임의 완화하지 않습니다.
5. SET AUTOCOMMIT FALSE;를 한 번 실행합니다. apply SQL을 직접 붙여 넣습니다. 오류가 하나라도 발생하면 같은 세션에서 즉시 ROLLBACK;합니다. 스키마 컬럼 누락 오류가 있으면 임의 기본값으로 우회하지 않고 배포 버전을 확인합니다.
6. UPDATE count는 실제 INSERT 건수입니다. 같은 트랜잭션에서 preview를 다시 실행해 후보 0건인지 확인합니다. 기존 응답을 유지했는지 표본 조회합니다. 검증되면 COMMIT; 후 quit합니다. SQL에 자동 COMMIT은 없습니다.
7. 서비스를 시작하고 active 확인 후 대상 계정으로 받은 제안 목록, pending-count, 원정 제안 응답 현황을 새로 조회합니다. 실제 수락·거절은 사용자 선택으로 진행합니다.

명령 파일:

- docs/sql/preview-missing-proposal-recipients.sql (조회만)
- docs/sql/backfill-missing-proposal-recipients.sql (INSERT만, 기존 데이터 UPDATE/DELETE 없음)

EC2에 실행한 사실이 없으므로 운영 보충 건수와 프런트 실제 응답 성공은 아직 미검증입니다. 적용 후 기록할 항목: 실행 시각(KST), PREVIEW 후보 수, INSERT 건수, 재조회 후보 0건, 재실행 0건, 기존 응답 표본, 서비스 상태, 대상 계정의 실제 API 조회/응답 결과.

## 자동 검증

ProposalRecipientBackfillSqlTest가 실제 위 SQL 파일을 별도 H2 메모리 DB에 실행합니다. 기존 응답/읽음/버전 보존, 발신자·타 팀·탈퇴자·마감 공고·제출 완료·마감 미확인 제외, 마감 당일 포함, 신규 PENDING 초기값, 중복 없는 재실행, 롤백과 커밋을 검증합니다.
