--
-- PostgreSQL database dump
--

\restrict kSLLd8SPCB5NE6jAZdfu2QOWAoTRvazIwIijx53y1yY2L5AcIB0lVET3c6S2Ays

-- Dumped from database version 18.6 (Ubuntu 18.6-0ubuntu0.26.04.1)
-- Dumped by pg_dump version 18.6 (Ubuntu 18.6-0ubuntu0.26.04.1)

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

SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: dead_letter_tasks; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.dead_letter_tasks (
    id uuid NOT NULL,
    task_id uuid NOT NULL,
    name character varying(255) NOT NULL,
    failure_reason text,
    retry_count integer NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: task_executions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.task_executions (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    task_id uuid NOT NULL,
    attempt_number integer NOT NULL,
    status character varying(20) NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    completed_at timestamp without time zone
);


--
-- Name: tasks; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tasks (
    id uuid NOT NULL,
    name character varying(255) NOT NULL,
    status character varying(20) NOT NULL,
    retry_count integer DEFAULT 0 NOT NULL,
    max_retries integer DEFAULT 3 NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    locked_at timestamp without time zone,
    priority character varying(20) DEFAULT 'MEDIUM'::character varying NOT NULL,
    idempotency_key character varying(255)
);


--
-- Name: dead_letter_tasks dead_letter_tasks_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dead_letter_tasks
    ADD CONSTRAINT dead_letter_tasks_pkey PRIMARY KEY (id);


--
-- Name: task_executions task_executions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.task_executions
    ADD CONSTRAINT task_executions_pkey PRIMARY KEY (id);


--
-- Name: tasks tasks_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tasks
    ADD CONSTRAINT tasks_pkey PRIMARY KEY (id);


--
-- Name: dead_letter_tasks unique_dlq_task; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dead_letter_tasks
    ADD CONSTRAINT unique_dlq_task UNIQUE (task_id);


--
-- Name: task_executions unique_task_attempt; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.task_executions
    ADD CONSTRAINT unique_task_attempt UNIQUE (task_id, attempt_number);


--
-- Name: tasks unique_task_idempotency_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tasks
    ADD CONSTRAINT unique_task_idempotency_key UNIQUE (idempotency_key);


--
-- Name: idx_dead_letter_task_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_dead_letter_task_id ON public.dead_letter_tasks USING btree (task_id);


--
-- Name: idx_tasks_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tasks_status ON public.tasks USING btree (status);


--
-- Name: dead_letter_tasks fk_dead_letter_task; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dead_letter_tasks
    ADD CONSTRAINT fk_dead_letter_task FOREIGN KEY (task_id) REFERENCES public.tasks(id);


--
-- Name: task_executions fk_task_execution; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.task_executions
    ADD CONSTRAINT fk_task_execution FOREIGN KEY (task_id) REFERENCES public.tasks(id) ON DELETE CASCADE;


--
-- PostgreSQL database dump complete
--

\unrestrict kSLLd8SPCB5NE6jAZdfu2QOWAoTRvazIwIijx53y1yY2L5AcIB0lVET3c6S2Ays

