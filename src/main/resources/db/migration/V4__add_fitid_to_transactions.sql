-- Identificador que o banco atribui à transação no OFX (<FITID>). Nulo para CSV,
-- lançamentos manuais e OFX cujo FITID se repete no próprio arquivo (não confiável).
-- Quando presente, unique_hash é derivado dele (conta + FITID) e não do conteúdo,
-- então o banco pode mudar a descrição entre exportações sem gerar duplicata.
-- 255 é o limite da especificação OFX.
ALTER TABLE transactions ADD COLUMN fitid VARCHAR(255);
