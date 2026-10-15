CREATE TABLE company (
                         id                  INTEGER         PRIMARY KEY AUTOINCREMENT,
                         legal_name          VARCHAR(255)    NOT NULL,
                         trade_name          VARCHAR(255),
                         cnpj                VARCHAR(14)     NOT NULL UNIQUE,
                         dominio_sistemas_id INT             NOT NULL,
                         sefaz_login         VARCHAR(255)    NOT NULL,
                         sefaz_password      VARCHAR(255)    NOT NULL,
                         responsible_user_id BIGINT          NOT NULL
);