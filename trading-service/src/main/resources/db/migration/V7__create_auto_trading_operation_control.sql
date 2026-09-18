CREATE TABLE trading.p_auto_trading_operation_controls (
   id UUID PRIMARY KEY,

   suspended BOOLEAN NOT NULL DEFAULT FALSE,

   created_at TIMESTAMPTZ NOT NULL,
   created_by UUID,
   updated_at TIMESTAMPTZ,
   updated_by UUID,
   deleted_at TIMESTAMPTZ,
   deleted_by UUID
);

INSERT INTO trading.p_auto_trading_operation_controls (
    id,
    suspended,
    created_at
)
VALUES (
           '00000000-0000-0000-0000-000000000001',
           FALSE,
           CURRENT_TIMESTAMP
       );