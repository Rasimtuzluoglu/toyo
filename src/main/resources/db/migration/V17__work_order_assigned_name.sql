alter table work_orders add column assigned_name varchar(100);

update work_orders w
set assigned_name = u.full_name
from users u
where w.assigned_user_id = u.id;
