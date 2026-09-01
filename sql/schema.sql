
-- Distributed Task Queue Database Schema


CREATE EXTENSION IF NOT EXISTS pgcrypto;




CREATE TABLE public.dead_letter_tasks (
    id uuid NOT NULL,
    task_id uuid NOT NULL,
    name character varying(255) NOT NULL,
    failure_reason text,
    retry_count integer NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);



CREATE TABLE public.task_executions (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    task_id uuid NOT NULL,
    attempt_number integer NOT NULL,
    status character varying(20) NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    completed_at timestamp without time zone
);



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



ALTER TABLE ONLY public.dead_letter_tasks
    ADD CONSTRAINT dead_letter_tasks_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.task_executions
    ADD CONSTRAINT task_executions_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.tasks
    ADD CONSTRAINT tasks_pkey PRIMARY KEY (id);


ALTER TABLE ONLY public.dead_letter_tasks
    ADD CONSTRAINT unique_dlq_task UNIQUE (task_id);

ALTER TABLE ONLY public.task_executions
    ADD CONSTRAINT unique_task_attempt UNIQUE (task_id, attempt_number);

ALTER TABLE ONLY public.tasks
    ADD CONSTRAINT unique_task_idempotency_key UNIQUE (idempotency_key);



CREATE INDEX idx_dead_letter_task_id
    ON public.dead_letter_tasks USING btree (task_id);

CREATE INDEX idx_tasks_status
    ON public.tasks USING btree (status);


ALTER TABLE ONLY public.dead_letter_tasks
    ADD CONSTRAINT fk_dead_letter_task
    FOREIGN KEY (task_id)
    REFERENCES public.tasks(id);

ALTER TABLE ONLY public.task_executions
    ADD CONSTRAINT fk_task_execution
    FOREIGN KEY (task_id)
    REFERENCES public.tasks(id)
    ON DELETE CASCADE;

