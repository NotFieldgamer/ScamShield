-- V2__seed_reference_data.sql — the active served model and the scam-phrase registry.

INSERT INTO model_versions (name, version, metrics, artifact_path, active) VALUES
 ('served-logreg-word-char-tfidf', '0.1.0',
  '{"note":"calibrated logistic regression over word+char TF-IDF; metrics in ml/README.md"}'::jsonb,
  'models/classifier.onnx', TRUE);

-- Aho-Corasick scans these in one pass. Weights are advisory severity, not model coefficients.
INSERT INTO scam_phrases (phrase, weight, category) VALUES
 ('no experience needed', 0.40, 'EMPLOYMENT'),
 ('no experience required', 0.40, 'EMPLOYMENT'),
 ('work from home', 0.30, 'EMPLOYMENT'),
 ('immediate start', 0.40, 'EMPLOYMENT'),
 ('start immediately', 0.35, 'EMPLOYMENT'),
 ('urgent hiring', 0.50, 'EMPLOYMENT'),
 ('limited slots', 0.45, 'URGENCY'),
 ('act now', 0.40, 'URGENCY'),
 ('earn daily', 0.70, 'EARNINGS'),
 ('earn up to', 0.40, 'EARNINGS'),
 ('guaranteed income', 0.70, 'EARNINGS'),
 ('unlimited earning', 0.70, 'EARNINGS'),
 ('double your income', 0.75, 'EARNINGS'),
 ('weekly payout', 0.50, 'EARNINGS'),
 ('processing fee', 0.90, 'PAYMENT'),
 ('registration fee', 0.90, 'PAYMENT'),
 ('security deposit', 0.80, 'PAYMENT'),
 ('pay a fee', 0.90, 'PAYMENT'),
 ('wire transfer', 0.80, 'PAYMENT'),
 ('western union', 0.90, 'PAYMENT'),
 ('gift card', 0.85, 'PAYMENT'),
 ('cryptocurrency', 0.45, 'PAYMENT'),
 ('bank account details', 0.85, 'PII'),
 ('send your bank', 0.80, 'PII'),
 ('credit card details', 0.85, 'PII'),
 ('copy of your passport', 0.70, 'PII'),
 ('social security number', 0.70, 'PII'),
 ('whatsapp', 0.50, 'CONTACT'),
 ('telegram', 0.55, 'CONTACT'),
 ('text me on', 0.50, 'CONTACT');
