--
-- V1 - baseline.
--
-- The schema as ddl-auto left it on 30 August 2026, taken with pg_dump. It is
-- not a design and it is not tidy; it is the honest starting point, so that
-- every migration after it is deliberate. Task 0.7 / defence risk R12.
--
-- Three lines from the raw dump were removed, because Flyway runs SQL through
-- JDBC and not through psql:
--
--   \\restrict / \\unrestrict   psql meta-commands. pg_dump 15.18 emits these;
--                              JDBC sees them as a syntax error on line 5 and
--                              the whole migration fails.
--   SELECT set_config('search_path','',false)
--                              empties the search path for the rest of the
--                              connection. Everything below is schema-qualified
--                              so the dump does not need it, but Flyway's own
--                              INSERT into flyway_schema_history afterwards is
--                              not, and would not resolve.
--
-- Nothing else was changed.
--
--
-- PostgreSQL database dump
--


-- Dumped from database version 15.18
-- Dumped by pg_dump version 15.18

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: appointment; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.appointment (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    end_time timestamp(6) without time zone NOT NULL,
    notes character varying(500),
    origin_node_id character varying(255) NOT NULL,
    payment_status character varying(255) NOT NULL,
    price_at_booking numeric(10,2) NOT NULL,
    source character varying(255) NOT NULL,
    start_time timestamp(6) without time zone NOT NULL,
    status character varying(255) NOT NULL,
    updated_at timestamp(6) without time zone,
    walk_in_contact character varying(255),
    walk_in_name character varying(255),
    branch_id uuid NOT NULL,
    customer_id uuid,
    form_id uuid,
    room_id uuid NOT NULL,
    service_id uuid NOT NULL,
    therapist_id uuid NOT NULL,
    CONSTRAINT appointment_has_a_client CHECK (((customer_id IS NOT NULL) OR (walk_in_name IS NOT NULL))),
    CONSTRAINT appointment_payment_status_check CHECK (((payment_status)::text = ANY ((ARRAY['UNPAID'::character varying, 'PAID_AT_COUNTER'::character varying])::text[]))),
    CONSTRAINT appointment_source_check CHECK (((source)::text = ANY ((ARRAY['CHATBOT'::character varying, 'STAFF_MANUAL'::character varying])::text[]))),
    CONSTRAINT appointment_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'CONFIRMED'::character varying, 'IN_PROGRESS'::character varying, 'COMPLETED'::character varying, 'CANCELLED'::character varying, 'NO_SHOW'::character varying])::text[])))
);


--
-- Name: audit_log; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.audit_log (
    id uuid NOT NULL,
    action character varying(255) NOT NULL,
    details character varying(1000),
    entity_id uuid NOT NULL,
    entity_type character varying(255) NOT NULL,
    occurred_at timestamp(6) without time zone NOT NULL,
    origin_node_id character varying(255) NOT NULL,
    actor_id uuid,
    branch_id uuid
);


--
-- Name: branch; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.branch (
    id uuid NOT NULL,
    address character varying(255) NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    name character varying(255) NOT NULL
);


--
-- Name: demographics; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.demographics (
    id uuid NOT NULL,
    age integer NOT NULL,
    birth_date date NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    height integer,
    occupation character varying(255),
    sex character varying(255) NOT NULL,
    status character varying(255) NOT NULL,
    weight integer,
    users_id uuid
);


--
-- Name: forms; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.forms (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    had_illness boolean,
    has_therapy boolean,
    intent character varying(255) NOT NULL,
    main_complaint character varying(255),
    main_complaint_duration character varying(255),
    main_complaint_other character varying(255),
    medical_history text,
    remarks character varying(255),
    status character varying(255),
    therapy_detail text,
    branch_id uuid NOT NULL,
    users_id uuid,
    walk_in_name character varying(255),
    pressure_preference character varying(255),
    CONSTRAINT forms_has_a_client CHECK (((users_id IS NOT NULL) OR (walk_in_name IS NOT NULL))),
    CONSTRAINT forms_intent_check CHECK (((intent)::text = ANY ((ARRAY['PAIN'::character varying, 'LEISURE'::character varying])::text[]))),
    CONSTRAINT forms_main_complaint_check CHECK (((main_complaint)::text = ANY ((ARRAY['NECK_PAIN'::character varying, 'SHOULDER_PAIN'::character varying, 'UPPER_BACK_PAIN'::character varying, 'LOWER_BACK_PAIN'::character varying, 'ELBOW_PAIN'::character varying, 'WRIST_PAIN'::character varying, 'HIP_JOINT_PAIN'::character varying, 'KNEE_PAIN'::character varying, 'ANKLE_PAIN'::character varying, 'STIFF_NECK'::character varying, 'FROZEN_SHOULDER'::character varying, 'SCIATICA'::character varying, 'SCOLIOSIS'::character varying, 'OSTEOARTHRITIS'::character varying, 'SPONDYLOSIS'::character varying, 'DISC_BULGE'::character varying, 'SLIP_DISC'::character varying, 'DDD'::character varying, 'DISC_DESICCATION'::character varying, 'STENOSIS'::character varying, 'PLANTAR_FASCIITIS'::character varying, 'RADICULOPATHY'::character varying, 'CTS'::character varying, 'TMJ_DISORDER'::character varying, 'OTHER'::character varying])::text[]))),
    CONSTRAINT forms_pressure_preference_check CHECK (((pressure_preference)::text = ANY ((ARRAY['LIGHT'::character varying, 'MEDIUM'::character varying, 'FIRM'::character varying])::text[])))
);


--
-- Name: forms_safety_flag; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.forms_safety_flag (
    forms_id uuid NOT NULL,
    flag character varying(255),
    CONSTRAINT forms_safety_flag_flag_check CHECK (((flag)::text = ANY ((ARRAY['PREGNANT'::character varying, 'HIGH_BLOOD_PRESSURE'::character varying, 'HEART_CONDITION'::character varying, 'DIABETES'::character varying, 'VARICOSE_VEINS'::character varying, 'RECENT_FRACTURE_OR_SURGERY'::character varying, 'OPEN_WOUND_OR_SKIN_INFECTION'::character varying, 'CANCER_OR_UNDER_TREATMENT'::character varying, 'BLOOD_THINNERS'::character varying, 'OSTEOPOROSIS'::character varying])::text[])))
);


--
-- Name: massage; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.massage (
    id uuid NOT NULL,
    duration_minute integer NOT NULL,
    name character varying(255) NOT NULL,
    price numeric(38,2) NOT NULL,
    active boolean
);


--
-- Name: patient_intake; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.patient_intake (
    id uuid NOT NULL,
    anatomical_region character varying(255) NOT NULL,
    body_view character varying(255) NOT NULL,
    complaint_type character varying(255),
    coordinatex integer NOT NULL,
    coordinatey integer NOT NULL,
    pain_score_after integer,
    pain_score_before integer NOT NULL,
    side character varying(255),
    form_id uuid NOT NULL,
    CONSTRAINT patient_intake_anatomical_region_check CHECK (((anatomical_region)::text = ANY ((ARRAY['CERVICAL'::character varying, 'SHOULDER'::character varying, 'ELBOW'::character varying, 'WRIST'::character varying, 'THORACIC'::character varying, 'MID_BACK'::character varying, 'LUMBAR'::character varying, 'SI_JOINT'::character varying, 'HIP_JOINT'::character varying, 'KNEE'::character varying, 'ANKLE'::character varying])::text[]))),
    CONSTRAINT patient_intake_body_view_check CHECK (((body_view)::text = ANY ((ARRAY['FRONT'::character varying, 'BACK'::character varying])::text[]))),
    CONSTRAINT patient_intake_complaint_type_check CHECK (((complaint_type)::text = ANY ((ARRAY['NECK_PAIN'::character varying, 'SHOULDER_PAIN'::character varying, 'UPPER_BACK_PAIN'::character varying, 'LOWER_BACK_PAIN'::character varying, 'ELBOW_PAIN'::character varying, 'WRIST_PAIN'::character varying, 'HIP_JOINT_PAIN'::character varying, 'KNEE_PAIN'::character varying, 'ANKLE_PAIN'::character varying, 'STIFF_NECK'::character varying, 'FROZEN_SHOULDER'::character varying, 'SCIATICA'::character varying, 'SCOLIOSIS'::character varying, 'OSTEOARTHRITIS'::character varying, 'SPONDYLOSIS'::character varying, 'DISC_BULGE'::character varying, 'SLIP_DISC'::character varying, 'DDD'::character varying, 'DISC_DESICCATION'::character varying, 'STENOSIS'::character varying, 'PLANTAR_FASCIITIS'::character varying, 'RADICULOPATHY'::character varying, 'CTS'::character varying, 'TMJ_DISORDER'::character varying, 'OTHER'::character varying])::text[]))),
    CONSTRAINT patient_intake_side_check CHECK (((side)::text = ANY ((ARRAY['LEFT'::character varying, 'RIGHT'::character varying, 'CENTRE'::character varying])::text[])))
);


--
-- Name: room; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.room (
    id uuid NOT NULL,
    active boolean NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    name character varying(255) NOT NULL,
    branch_id uuid NOT NULL
);


--
-- Name: service_protocol; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.service_protocol (
    id uuid NOT NULL,
    authored_by character varying(255) NOT NULL,
    condition character varying(255) NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    rationale character varying(500),
    rule character varying(255) NOT NULL,
    service_id uuid NOT NULL,
    CONSTRAINT service_protocol_condition_check CHECK (((condition)::text = ANY ((ARRAY['NECK_PAIN'::character varying, 'SHOULDER_PAIN'::character varying, 'UPPER_BACK_PAIN'::character varying, 'LOWER_BACK_PAIN'::character varying, 'ELBOW_PAIN'::character varying, 'WRIST_PAIN'::character varying, 'HIP_JOINT_PAIN'::character varying, 'KNEE_PAIN'::character varying, 'ANKLE_PAIN'::character varying, 'STIFF_NECK'::character varying, 'FROZEN_SHOULDER'::character varying, 'SCIATICA'::character varying, 'SCOLIOSIS'::character varying, 'OSTEOARTHRITIS'::character varying, 'SPONDYLOSIS'::character varying, 'DISC_BULGE'::character varying, 'SLIP_DISC'::character varying, 'DDD'::character varying, 'DISC_DESICCATION'::character varying, 'STENOSIS'::character varying, 'PLANTAR_FASCIITIS'::character varying, 'RADICULOPATHY'::character varying, 'CTS'::character varying, 'TMJ_DISORDER'::character varying, 'OTHER'::character varying])::text[]))),
    CONSTRAINT service_protocol_rule_check CHECK (((rule)::text = ANY ((ARRAY['INDICATED'::character varying, 'CONTRAINDICATED'::character varying])::text[])))
);


--
-- Name: therapist; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.therapist (
    id uuid NOT NULL,
    active boolean NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    first_name character varying(255) NOT NULL,
    last_name character varying(255) NOT NULL,
    status character varying(255) NOT NULL,
    branch_id uuid NOT NULL,
    CONSTRAINT therapist_status_check CHECK (((status)::text = ANY ((ARRAY['AVAILABLE'::character varying, 'BUSY'::character varying, 'ON_BREAK'::character varying, 'OFF_DUTY'::character varying])::text[])))
);


--
-- Name: users; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.users (
    id uuid NOT NULL,
    address character varying(255) NOT NULL,
    contact character varying(255) NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    email character varying(255) NOT NULL,
    enabled boolean NOT NULL,
    first_name character varying(255) NOT NULL,
    last_name character varying(255) NOT NULL,
    middle_name character varying(255),
    password_hash character varying(255) NOT NULL,
    role character varying(255) NOT NULL,
    branch_id uuid,
    CONSTRAINT users_role_check CHECK (((role)::text = ANY ((ARRAY['CUSTOMER'::character varying, 'STAFF'::character varying, 'ADMIN'::character varying])::text[])))
);


--
-- Name: appointment appointment_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.appointment
    ADD CONSTRAINT appointment_pkey PRIMARY KEY (id);


--
-- Name: audit_log audit_log_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.audit_log
    ADD CONSTRAINT audit_log_pkey PRIMARY KEY (id);


--
-- Name: branch branch_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.branch
    ADD CONSTRAINT branch_pkey PRIMARY KEY (id);


--
-- Name: demographics demographics_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.demographics
    ADD CONSTRAINT demographics_pkey PRIMARY KEY (id);


--
-- Name: forms forms_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.forms
    ADD CONSTRAINT forms_pkey PRIMARY KEY (id);


--
-- Name: massage massage_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.massage
    ADD CONSTRAINT massage_pkey PRIMARY KEY (id);


--
-- Name: patient_intake patient_intake_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.patient_intake
    ADD CONSTRAINT patient_intake_pkey PRIMARY KEY (id);


--
-- Name: room room_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.room
    ADD CONSTRAINT room_pkey PRIMARY KEY (id);


--
-- Name: service_protocol service_protocol_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.service_protocol
    ADD CONSTRAINT service_protocol_pkey PRIMARY KEY (id);


--
-- Name: therapist therapist_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.therapist
    ADD CONSTRAINT therapist_pkey PRIMARY KEY (id);


--
-- Name: users uk6dotkott2kjsp8vw4d0m25fb7; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT uk6dotkott2kjsp8vw4d0m25fb7 UNIQUE (email);


--
-- Name: demographics ukj4ut7almp3yb0wihl9uiuw78t; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.demographics
    ADD CONSTRAINT ukj4ut7almp3yb0wihl9uiuw78t UNIQUE (users_id);


--
-- Name: service_protocol ukpwmil63nkpmcu48uy4up7kg1g; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.service_protocol
    ADD CONSTRAINT ukpwmil63nkpmcu48uy4up7kg1g UNIQUE (service_id, condition);


--
-- Name: users users_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_pkey PRIMARY KEY (id);


--
-- Name: room fk3mf32q0hwpnmu7rf58gjtjrmj; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.room
    ADD CONSTRAINT fk3mf32q0hwpnmu7rf58gjtjrmj FOREIGN KEY (branch_id) REFERENCES public.branch(id);


--
-- Name: audit_log fk54kvh1he29arh3vwic9fmm8nf; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.audit_log
    ADD CONSTRAINT fk54kvh1he29arh3vwic9fmm8nf FOREIGN KEY (branch_id) REFERENCES public.branch(id);


--
-- Name: patient_intake fk6nuvk5eqe27kvgodcji1fdgvq; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.patient_intake
    ADD CONSTRAINT fk6nuvk5eqe27kvgodcji1fdgvq FOREIGN KEY (form_id) REFERENCES public.forms(id);


--
-- Name: forms fk7c62vlq9hnt3o1od1lp0bp7wi; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.forms
    ADD CONSTRAINT fk7c62vlq9hnt3o1od1lp0bp7wi FOREIGN KEY (users_id) REFERENCES public.users(id);


--
-- Name: appointment fk8yxiq8d6ubccrih94xicd2l5b; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.appointment
    ADD CONSTRAINT fk8yxiq8d6ubccrih94xicd2l5b FOREIGN KEY (room_id) REFERENCES public.room(id);


--
-- Name: forms fkd2n94ennkh8cbg38j3pm5fy0h; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.forms
    ADD CONSTRAINT fkd2n94ennkh8cbg38j3pm5fy0h FOREIGN KEY (branch_id) REFERENCES public.branch(id);


--
-- Name: therapist fki5kr0mxeexupxoo912pd8rgoh; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.therapist
    ADD CONSTRAINT fki5kr0mxeexupxoo912pd8rgoh FOREIGN KEY (branch_id) REFERENCES public.branch(id);


--
-- Name: appointment fki71h8mcr2o9jv5mgpjtf2spuc; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.appointment
    ADD CONSTRAINT fki71h8mcr2o9jv5mgpjtf2spuc FOREIGN KEY (customer_id) REFERENCES public.users(id);


--
-- Name: appointment fkinldnpibg7rybxk7j099hc565; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.appointment
    ADD CONSTRAINT fkinldnpibg7rybxk7j099hc565 FOREIGN KEY (service_id) REFERENCES public.massage(id);


--
-- Name: appointment fkirq7r526btqxyk1gsuq4wa2h3; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.appointment
    ADD CONSTRAINT fkirq7r526btqxyk1gsuq4wa2h3 FOREIGN KEY (branch_id) REFERENCES public.branch(id);


--
-- Name: users fkixo09sv3j1j6hfox3cx6d2ggg; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT fkixo09sv3j1j6hfox3cx6d2ggg FOREIGN KEY (branch_id) REFERENCES public.branch(id);


--
-- Name: service_protocol fkjq9q6c9ur4rhn8obhg9f85d5j; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.service_protocol
    ADD CONSTRAINT fkjq9q6c9ur4rhn8obhg9f85d5j FOREIGN KEY (service_id) REFERENCES public.massage(id);


--
-- Name: appointment fkly7yii6c3uhd3bwomw67camxr; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.appointment
    ADD CONSTRAINT fkly7yii6c3uhd3bwomw67camxr FOREIGN KEY (therapist_id) REFERENCES public.therapist(id);


--
-- Name: appointment fkn4yxu3xjh5xnhs3ckos7tix7o; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.appointment
    ADD CONSTRAINT fkn4yxu3xjh5xnhs3ckos7tix7o FOREIGN KEY (form_id) REFERENCES public.forms(id);


--
-- Name: demographics fkocq9asm7rgl284f4da2xt8yxo; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.demographics
    ADD CONSTRAINT fkocq9asm7rgl284f4da2xt8yxo FOREIGN KEY (users_id) REFERENCES public.users(id);


--
-- Name: audit_log fkp0xyrkkoeraheio5qt1iihlo1; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.audit_log
    ADD CONSTRAINT fkp0xyrkkoeraheio5qt1iihlo1 FOREIGN KEY (actor_id) REFERENCES public.users(id);


--
-- Name: forms_safety_flag fks5iji418wmx8yewfu7rji9thu; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.forms_safety_flag
    ADD CONSTRAINT fks5iji418wmx8yewfu7rji9thu FOREIGN KEY (forms_id) REFERENCES public.forms(id);


--
-- PostgreSQL database dump complete
--


