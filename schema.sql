--
-- PostgreSQL database dump
--

\restrict YloYTb4XPVCuK9b6l9DEOIYEircevibmNYBIP9J4fuKEp4igsvwtyOEEhLodOQj

-- Dumped from database version 17.7
-- Dumped by pg_dump version 17.7 (Ubuntu 17.7-3.pgdg24.04+1)

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: google_vacuum_mgmt; Type: SCHEMA; Schema: -; Owner: -
--

CREATE SCHEMA google_vacuum_mgmt;


--
-- Name: google_vacuum_mgmt; Type: EXTENSION; Schema: -; Owner: -
--

CREATE EXTENSION IF NOT EXISTS google_vacuum_mgmt WITH SCHEMA google_vacuum_mgmt;


--
-- Name: EXTENSION google_vacuum_mgmt; Type: COMMENT; Schema: -; Owner: -
--

COMMENT ON EXTENSION google_vacuum_mgmt IS 'extension for assistive operational tooling';


--
-- Name: pgcrypto; Type: EXTENSION; Schema: -; Owner: -
--

CREATE EXTENSION IF NOT EXISTS pgcrypto WITH SCHEMA public;


--
-- Name: EXTENSION pgcrypto; Type: COMMENT; Schema: -; Owner: -
--

COMMENT ON EXTENSION pgcrypto IS 'cryptographic functions';


--
-- Name: trg_crear_cuenta_usuario(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.trg_crear_cuenta_usuario() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
  INSERT INTO public.cuentas(curp, saldo_billetera, saldo_banco)
  VALUES (NEW.curp, 0, 0)
  ON CONFLICT (curp) DO NOTHING;
  RETURN NEW;
END;
$$;


--
-- Name: trg_set_event_id(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.trg_set_event_id() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
  IF NEW.event_id IS NULL OR NEW.event_id = '' THEN
    NEW.event_id := NEW.id::text;
  END IF;
  RETURN NEW;
END;
$$;


SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: auditoria; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.auditoria (
    id bigint NOT NULL,
    transaccion_id uuid,
    tipo_evento text NOT NULL,
    payload_json jsonb NOT NULL,
    creado_en timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: auditoria_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.auditoria_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: auditoria_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.auditoria_id_seq OWNED BY public.auditoria.id;


--
-- Name: cuentas; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.cuentas (
    curp character varying(18) NOT NULL,
    saldo_billetera numeric(14,2) DEFAULT 0 NOT NULL,
    saldo_banco numeric(14,2) DEFAULT 0 NOT NULL,
    actualizado_en timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT cuentas_saldo_banco_check CHECK ((saldo_banco >= (0)::numeric)),
    CONSTRAINT cuentas_saldo_billetera_check CHECK ((saldo_billetera >= (0)::numeric))
);


--
-- Name: transacciones; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.transacciones (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    event_id text NOT NULL,
    curp_origen character varying(18),
    curp_destino character varying(18),
    monto numeric(14,2) NOT NULL,
    tipo character varying(12) NOT NULL,
    estado character varying(12) NOT NULL,
    creado_en timestamp with time zone DEFAULT now() NOT NULL,
    confirmado_en timestamp with time zone,
    CONSTRAINT chk_tx_campos_por_tipo CHECK (((((tipo)::text = 'DEPOSITO'::text) AND (curp_destino IS NOT NULL) AND (curp_origen IS NULL)) OR (((tipo)::text = 'RETIRO'::text) AND (curp_origen IS NOT NULL) AND (curp_destino IS NULL)) OR (((tipo)::text = 'TRANSFERENCIA'::text) AND (curp_origen IS NOT NULL) AND (curp_destino IS NOT NULL) AND ((curp_origen)::text <> (curp_destino)::text)))),
    CONSTRAINT chk_tx_confirmado_en_estado CHECK (((((estado)::text = 'PENDIENTE'::text) AND (confirmado_en IS NULL)) OR (((estado)::text = ANY ((ARRAY['CONFIRMADA'::character varying, 'FALLIDA'::character varying])::text[])) AND (confirmado_en IS NOT NULL)))),
    CONSTRAINT transacciones_estado_check CHECK (((estado)::text = ANY ((ARRAY['PENDIENTE'::character varying, 'CONFIRMADA'::character varying, 'FALLIDA'::character varying])::text[]))),
    CONSTRAINT transacciones_monto_check CHECK ((monto > (0)::numeric)),
    CONSTRAINT transacciones_tipo_check CHECK (((tipo)::text = ANY ((ARRAY['DEPOSITO'::character varying, 'RETIRO'::character varying, 'TRANSFERENCIA'::character varying])::text[])))
);


--
-- Name: usuarios; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.usuarios (
    curp character varying(18) NOT NULL,
    hash_contrasena text NOT NULL,
    rol character varying(10) NOT NULL,
    creado_en timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT usuarios_rol_check CHECK (((rol)::text = ANY ((ARRAY['USUARIO'::character varying, 'ADMIN'::character varying])::text[])))
);


--
-- Name: vista_saldo_total; Type: VIEW; Schema: public; Owner: -
--

CREATE VIEW public.vista_saldo_total AS
 SELECT COALESCE(sum((saldo_billetera + saldo_banco)), (0)::numeric) AS saldo_total
   FROM public.cuentas;


--
-- Name: auditoria id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.auditoria ALTER COLUMN id SET DEFAULT nextval('public.auditoria_id_seq'::regclass);


--
-- Name: auditoria auditoria_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.auditoria
    ADD CONSTRAINT auditoria_pkey PRIMARY KEY (id);


--
-- Name: cuentas cuentas_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cuentas
    ADD CONSTRAINT cuentas_pkey PRIMARY KEY (curp);


--
-- Name: transacciones transacciones_event_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.transacciones
    ADD CONSTRAINT transacciones_event_id_key UNIQUE (event_id);


--
-- Name: transacciones transacciones_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.transacciones
    ADD CONSTRAINT transacciones_pkey PRIMARY KEY (id);


--
-- Name: usuarios usuarios_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.usuarios
    ADD CONSTRAINT usuarios_pkey PRIMARY KEY (curp);


--
-- Name: idx_auditoria_transaccion; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_auditoria_transaccion ON public.auditoria USING btree (transaccion_id);


--
-- Name: idx_transacciones_creado_en; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_transacciones_creado_en ON public.transacciones USING btree (creado_en);


--
-- Name: idx_transacciones_destino; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_transacciones_destino ON public.transacciones USING btree (curp_destino);


--
-- Name: idx_transacciones_event_estado; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_transacciones_event_estado ON public.transacciones USING btree (event_id, estado);


--
-- Name: idx_transacciones_origen; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_transacciones_origen ON public.transacciones USING btree (curp_origen);


--
-- Name: usuarios crear_cuenta_after_insert_usuario; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER crear_cuenta_after_insert_usuario AFTER INSERT ON public.usuarios FOR EACH ROW EXECUTE FUNCTION public.trg_crear_cuenta_usuario();


--
-- Name: transacciones set_event_id_before_insert; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER set_event_id_before_insert BEFORE INSERT ON public.transacciones FOR EACH ROW EXECUTE FUNCTION public.trg_set_event_id();


--
-- Name: auditoria auditoria_transaccion_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.auditoria
    ADD CONSTRAINT auditoria_transaccion_id_fkey FOREIGN KEY (transaccion_id) REFERENCES public.transacciones(id) ON DELETE SET NULL;


--
-- Name: cuentas cuentas_curp_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cuentas
    ADD CONSTRAINT cuentas_curp_fkey FOREIGN KEY (curp) REFERENCES public.usuarios(curp) ON DELETE CASCADE;


--
-- Name: transacciones fk_tx_destino_usuario; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.transacciones
    ADD CONSTRAINT fk_tx_destino_usuario FOREIGN KEY (curp_destino) REFERENCES public.usuarios(curp) ON DELETE SET NULL;


--
-- Name: transacciones fk_tx_origen_usuario; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.transacciones
    ADD CONSTRAINT fk_tx_origen_usuario FOREIGN KEY (curp_origen) REFERENCES public.usuarios(curp) ON DELETE SET NULL;


--
-- PostgreSQL database dump complete
--

\unrestrict YloYTb4XPVCuK9b6l9DEOIYEircevibmNYBIP9J4fuKEp4igsvwtyOEEhLodOQj

